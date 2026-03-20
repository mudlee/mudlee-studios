package hu.mudlee.core.render.vulkan;

import static hu.mudlee.core.render.vulkan.VulkanCommandPool.FRAMES_IN_FLIGHT;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.ColorLoadAction;
import hu.mudlee.core.render.GraphicsContext;
import hu.mudlee.core.render.RenderPassOptions;
import hu.mudlee.core.render.RenderTarget;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.texture.Texture2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan implementation of GraphicsContext.
 *
 * <p>Singleton: set on construction, accessed via {@link #get()}. Instantiated by Renderer when the
 * Vulkan backend is selected.
 *
 * <p>Frame loop (called by Application each frame):
 *
 * <p>beginFrame() → wait fence → acquire swap chain image → begin command buffer → begin render
 * pass → renderRaw() → bind pipeline → push constants → bind texture descriptor set → draw →
 * end render pass → end command buffer → submit → present → advance frame index
 *
 * <p>Window resize is handled lazily: swapchainOutOfDate is set on windowResized() and the swap
 * chain is recreated at the next beginFrame() call.
 *
 * <p>Descriptor set layout for textures is owned here (not per-shader) so that VulkanTexture2D can
 * allocate and write its own descriptor set without knowing about any specific shader. VulkanShader
 * re-uses the same layout via {@link #textureDescriptorSetLayout()}.
 */
public class VulkanContext implements GraphicsContext {

    private static final Logger log = LoggerFactory.getLogger(VulkanContext.class);

    /** Maximum number of texture descriptor sets allocatable from the shared pool. */
    private static final int MAX_TEXTURE_DESCRIPTORS = 256;

    private static VulkanContext instance;

    private final boolean debug;

    // Core Vulkan objects (created in windowCreated())
    private VulkanInstance vkInstance;
    private long surface = VK_NULL_HANDLE;
    private VulkanDevice device;
    private VulkanSwapChain swapChain;
    private VulkanCommandPool commandPool;
    private VulkanSyncObjects syncObjects;
    private VulkanAllocator allocator;
    private final Map<VulkanRenderPassSpec, VulkanRenderPass> renderPassCache = new HashMap<>();

    // Global descriptor layout for combined-image-sampler at set=0, binding=0
    private long textureDescriptorSetLayout = VK_NULL_HANDLE;
    // Pool-of-pools: each pool is capped at MAX_TEXTURE_DESCRIPTORS; a new pool is created when all are full.
    private final List<Long> descriptorPools = new ArrayList<>();
    // Tracks which pool each allocated descriptor set came from, so it can be freed individually.
    private final Map<Long, Long> descriptorSetToPool = new HashMap<>();

    // Frame state
    private int currentFrame = 0;
    private int currentImageIndex = 0;
    private boolean swapchainOutOfDate = false;
    private boolean vSync = true;
    private long windowId = 0;
    private boolean renderPassActive = false;
    private boolean frameInProgress = false;
    private VulkanRenderTarget activeRenderTarget = null;
    private long activeRenderPassHandle = VK_NULL_HANDLE;
    private RenderPassOptions activeRenderPassOptions = RenderPassOptions.clearColor();
    private final List<Runnable>[] deferredReleases;

    private final float[] clearColor = {0f, 0f, 0f, 1f};
    private String rendererInfo = "";
    private boolean disposed = false;
    private final Set<VulkanShader> liveShaders = Collections.newSetFromMap(new WeakHashMap<>());

    @SuppressWarnings("unchecked")
    public VulkanContext(boolean debug) {
        this.debug = debug;
        this.deferredReleases = new List[FRAMES_IN_FLIGHT];
        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            deferredReleases[i] = new ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // Singleton access (used by VulkanShader, VulkanTexture2D)
    // -------------------------------------------------------------------------

    static VulkanContext get() {
        if (instance == null) {
            throw new IllegalStateException("VulkanContext has not been initialised yet");
        }
        return instance;
    }

    int currentFrame() {
        return currentFrame;
    }

    VulkanDevice device() {
        return device;
    }

    VulkanAllocator allocator() {
        return allocator;
    }

    VulkanCommandPool commandPool() {
        return commandPool;
    }

    /**
     * The descriptor set layout shared by all shaders and textures: set=0, binding=0, combined image
     * sampler, fragment stage.
     */
    long textureDescriptorSetLayout() {
        return textureDescriptorSetLayout;
    }

    VkExtent2D swapChainExtent() {
        return swapChain.extent();
    }

    boolean isDisposed() {
        return disposed;
    }

    void registerShader(VulkanShader shader) {
        liveShaders.add(shader);
    }

    void unregisterShader(VulkanShader shader) {
        liveShaders.remove(shader);
    }

    /**
     * Allocates a single descriptor set using the global texture layout. Tries each existing pool in
     * order; if all are exhausted, a new pool is created. Called by VulkanTexture2D and
     * VulkanRenderTarget during construction.
     */
    long allocateTextureDescriptorSet() {
        for (var pool : descriptorPools) {
            var set = tryAllocateFrom(pool);
            if (set != VK_NULL_HANDLE) {
                descriptorSetToPool.put(set, pool);
                return set;
            }
        }
        var newPool = createNewDescriptorPool();
        descriptorPools.add(newPool);
        var set = tryAllocateFrom(newPool);
        if (set == VK_NULL_HANDLE) {
            throw new RuntimeException("Failed to allocate texture descriptor set from fresh pool");
        }
        descriptorSetToPool.put(set, newPool);
        return set;
    }

    /**
     * Frees a descriptor set back to the pool it was allocated from. Called by VulkanTexture2D and
     * VulkanRenderTarget on dispose and resize.
     */
    void freeTextureDescriptorSet(long descriptorSet) {
        if (descriptorSet == VK_NULL_HANDLE) {
            return;
        }
        var pool = descriptorSetToPool.remove(descriptorSet);
        if (pool == null) {
            log.warn("freeTextureDescriptorSet: untracked descriptor set handle {}", descriptorSet);
            return;
        }
        try (MemoryStack stack = stackPush()) {
            vkFreeDescriptorSets(device.device(), pool, stack.longs(descriptorSet));
        }
    }

    private long tryAllocateFrom(long pool) {
        try (MemoryStack stack = stackPush()) {
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(pool)
                    .pSetLayouts(stack.longs(textureDescriptorSetLayout));
            var pSet = stack.mallocLong(1);
            var result = vkAllocateDescriptorSets(device.device(), allocInfo, pSet);
            return result == VK_SUCCESS ? pSet.get(0) : VK_NULL_HANDLE;
        }
    }

    // -------------------------------------------------------------------------
    // GraphicsContext lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void windowPrepared() {
        // Prevent GLFW from creating an OpenGL context — Vulkan creates its own surface
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        log.debug("VulkanContext: GLFW_CLIENT_API = GLFW_NO_API");
    }

    @Override
    public void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync) {
        log.debug("Initialising Vulkan context...");
        this.windowId = windowId;
        this.vSync = vSync;
        disposed = false;

        try {
            vkInstance = new VulkanInstance("MudleeEngine", debug);

            try (MemoryStack stack = stackPush()) {
                var pSurface = stack.mallocLong(1);
                var result = glfwCreateWindowSurface(vkInstance.handle(), windowId, null, pSurface);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create Vulkan window surface: " + result);
                }
                surface = pSurface.get(0);
            }

            device = new VulkanDevice(vkInstance, surface);
            allocator = new VulkanAllocator(vkInstance.handle(), device.physicalDevice(), device.device());
            commandPool = new VulkanCommandPool(device);
            swapChain = new VulkanSwapChain(device, allocator, commandPool, surface, windowId, vSync);
            syncObjects = new VulkanSyncObjects(device, swapChain.imageCount());
            createTextureDescriptorSetLayout();
            createDescriptorPool();

            instance = this;
            logDeviceInfo();
            log.debug("Vulkan context ready. vSync={}", vSync);
        } catch (RuntimeException | Error e) {
            disposeInternal(false);
            throw e;
        }
    }

    @Override
    public void setClearColor(Vector4f color) {
        clearColor[0] = color.x;
        clearColor[1] = color.y;
        clearColor[2] = color.z;
        clearColor[3] = color.w;
    }

    /**
     * Begins a new frame: waits for the GPU fence, acquires the next swap chain image, and begins
     * command buffer recording. Render passes are started explicitly via {@link
     * #beginRenderPass(RenderTarget)}.
     */
    @Override
    public boolean beginFrame() {
        resetFrameState();

        if (swapchainOutOfDate) {
            recreateSwapChain();
        }

        try (MemoryStack stack = stackPush()) {
            var fence = syncObjects.inFlightFence(currentFrame);
            vkWaitForFences(device.device(), fence, true, Long.MAX_VALUE);
            flushDeferredReleases(currentFrame);

            var pImageIndex = stack.mallocInt(1);
            var result = vkAcquireNextImageKHR(
                    device.device(),
                    swapChain.swapChainHandle(),
                    Long.MAX_VALUE,
                    syncObjects.imageAvailableSemaphore(currentFrame),
                    VK_NULL_HANDLE,
                    pImageIndex);

            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapChain();
                return false;
            } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swap chain image: " + result);
            }

            currentImageIndex = pImageIndex.get(0);

            // Reset fence only after a successful acquisition to avoid deadlocks on resize
            vkResetFences(device.device(), fence);

            var cmdBuf = commandPool.commandBuffer(currentFrame);
            vkResetCommandBuffer(cmdBuf, 0);

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            if (vkBeginCommandBuffer(cmdBuf, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin command buffer");
            }
        }

        frameInProgress = true;
        return true;
    }

    @Override
    public void beginRenderPass(RenderTarget renderTarget, RenderPassOptions options) {
        if (!frameInProgress) {
            return;
        }
        var cmdBuf = commandPool.commandBuffer(currentFrame);
        if (renderPassActive) {
            vkCmdEndRenderPass(cmdBuf);
            renderPassActive = false;
        }
        activeRenderTarget = (renderTarget instanceof VulkanRenderTarget vrt) ? vrt : null;
        activeRenderPassOptions = options;
        beginCurrentRenderPass();
    }

    @Override
    public void endRenderPass() {
        if (!frameInProgress || !renderPassActive) {
            return;
        }
        var cmdBuf = commandPool.commandBuffer(currentFrame);
        vkCmdEndRenderPass(cmdBuf);
        renderPassActive = false;
        activeRenderTarget = null;
        activeRenderPassHandle = VK_NULL_HANDLE;
    }

    /**
     * Records a draw call into the current command buffer. beginFrame() must have succeeded earlier
     * in the same frame.
     *
     * <p>Per Vulkan best practice: – Matrices pushed as push constants (no per-frame UBO allocation).
     * – Texture bound via a pre-built descriptor set (written at texture creation time).
     */
    @Override
    public void renderRaw(VertexArray vertexArray, Shader shader) {
        renderRawInternal(vertexArray, shader, null, -1, 0);
    }

    @Override
    public void renderRaw(VertexArray vertexArray, Shader shader, int elementOffset, int elementCount) {
        renderRawInternal(vertexArray, shader, null, elementCount, elementOffset);
    }

    @Override
    public void renderRaw(
            VertexArray vertexArray, Shader shader, Texture2D texture, int elementOffset, int elementCount) {
        renderRawInternal(vertexArray, shader, texture, elementCount, elementOffset);
    }

    private void renderRawInternal(
            VertexArray vertexArray, Shader shader, Texture2D texture, int elementCount, int elementOffset) {
        if (!frameInProgress) {
            return;
        }
        if (!renderPassActive) {
            throw new IllegalStateException("renderRaw requires an active render pass");
        }
        if (!(shader instanceof VulkanShader vs)) {
            throw new IllegalArgumentException("VulkanContext requires a VulkanShader");
        }
        if (!(vertexArray instanceof VulkanVertexArray va)) {
            throw new IllegalArgumentException("VulkanContext requires a VulkanVertexArray");
        }
        if (va.getVBOs().isEmpty()) {
            return;
        }

        var cmdBuf = commandPool.commandBuffer(currentFrame);

        // Create the VkPipeline lazily using the currently active render pass and extent
        var boundVertexBuffers = va.vertexBuffers();
        var firstVbo = boundVertexBuffers.get(0);
        var currentRpHandle = activeRenderPassHandle;
        var currentExtent = activeRenderTarget != null ? activeRenderTarget.extent() : swapChain.extent();
        var pipeline = vs.getOrCreatePipeline(boundVertexBuffers, currentRpHandle, currentExtent);

        vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

        try (MemoryStack stack = stackPush()) {
            var pushData = stack.mallocFloat(vs.pushConstantFloatCount());
            pushData.put(vs.projectionData()).put(vs.viewData());
            if (vs.pushConstantFloatCount() > 32) {
                pushData.put(vs.modelData());
            }
            pushData.flip();
            vkCmdPushConstants(cmdBuf, vs.pipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushData);

            // Bind texture descriptor set (set=0)
            if (texture instanceof VulkanTextureBinding binding && binding.descriptorSetHandle() != VK_NULL_HANDLE) {
                var pSet = stack.longs(binding.descriptorSetHandle());
                vkCmdBindDescriptorSets(cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, vs.pipelineLayout(), 0, pSet, null);
            }

            // Bind vertex buffers
            var vboCount = boundVertexBuffers.size();
            var pBuffers = stack.mallocLong(vboCount);
            var pOffsets = stack.callocLong(vboCount);
            for (var vb : boundVertexBuffers) {
                pBuffers.put(vb.bufferHandle());
            }
            pBuffers.flip();
            vkCmdBindVertexBuffers(cmdBuf, 0, pBuffers, pOffsets);

            // Draw indexed or non-indexed
            if (va.getEBO().isPresent() && va.getEBO().get() instanceof VulkanIndexBuffer ib) {
                vkCmdBindIndexBuffer(cmdBuf, ib.bufferHandle(), 0, VK_INDEX_TYPE_UINT32);
                var actualCount = elementCount < 0 ? ib.getLength() : elementCount;
                var instanceCount = va.isInstanced() ? va.getInstanceCount() : 1;
                vkCmdDrawIndexed(cmdBuf, actualCount, instanceCount, elementOffset, 0, 0);
            } else {
                // Derive vertex count from buffer length and stride
                var stride =
                        firstVbo.getLayout().stride() > 0 ? firstVbo.getLayout().stride() : Float.BYTES;
                var vertexCount = (firstVbo.getLength() * Float.BYTES) / stride;
                var instanceCount = va.isInstanced() ? va.getInstanceCount() : 1;
                vkCmdDraw(cmdBuf, vertexCount, instanceCount, 0, 0);
            }
        }
    }

    /** Ends the render pass, submits the command buffer to the graphics queue, and presents. */
    @Override
    public void present(float frameTime) {
        if (!frameInProgress) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            var cmdBuf = commandPool.commandBuffer(currentFrame);

            if (renderPassActive) {
                vkCmdEndRenderPass(cmdBuf);
                renderPassActive = false;
            }
            if (vkEndCommandBuffer(cmdBuf) != VK_SUCCESS) {
                throw new RuntimeException("Failed to end command buffer");
            }

            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(syncObjects.imageAvailableSemaphore(currentFrame)))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(cmdBuf))
                    .pSignalSemaphores(stack.longs(syncObjects.renderFinishedSemaphore(currentImageIndex)));

            var result = vkQueueSubmit(device.graphicsQueue(), submitInfo, syncObjects.inFlightFence(currentFrame));
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit command buffer: " + result);
            }

            var presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(syncObjects.renderFinishedSemaphore(currentImageIndex)))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapChain.swapChainHandle()))
                    .pImageIndices(stack.ints(currentImageIndex));

            result = vkQueuePresentKHR(device.presentQueue(), presentInfo);
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                swapchainOutOfDate = true;
            } else if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to present swap chain image: " + result);
            }

            currentFrame = (currentFrame + 1) % FRAMES_IN_FLIGHT;
        }
        resetFrameState();
    }

    @Override
    public void windowResized(int newWidth, int newHeight) {
        swapchainOutOfDate = true;
    }

    @Override
    public void waitIdle() {
        device.waitIdle();
    }

    @Override
    public void dispose() {
        disposeInternal(true);
        log.debug("VulkanContext disposed");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void beginCurrentRenderPass() {
        try (MemoryStack stack = stackPush()) {
            var cmdBuf = commandPool.commandBuffer(currentFrame);

            long rpHandle;
            long fbHandle;
            int w, h;

            var spec = activeRenderTarget != null
                    ? renderTargetPassSpec(activeRenderPassOptions)
                    : backbufferPassSpec(activeRenderPassOptions);
            var clearValues = VkClearValue.calloc(spec.hasDepthAttachment() ? 2 : 1, stack);
            clearValues
                    .get(0)
                    .color()
                    .float32(0, clearColor[0])
                    .float32(1, clearColor[1])
                    .float32(2, clearColor[2])
                    .float32(3, clearColor[3]);
            if (spec.hasDepthAttachment()) {
                clearValues.get(1).depthStencil().depth(1f).stencil(0);
            }
            if (activeRenderTarget != null) {
                var renderPass = getOrCreateRenderPass(spec);
                rpHandle = renderPass.handle();
                fbHandle = activeRenderTarget.framebufferHandle(spec, rpHandle);
                w = activeRenderTarget.vkWidth();
                h = activeRenderTarget.vkHeight();
            } else {
                var renderPass = getOrCreateRenderPass(spec);
                rpHandle = renderPass.handle();
                fbHandle = swapChain.framebuffer(currentImageIndex, spec, rpHandle);
                w = swapChain.extent().width();
                h = swapChain.extent().height();
            }

            var rpBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(rpHandle)
                    .framebuffer(fbHandle)
                    .pClearValues(clearValues);
            rpBeginInfo.renderArea().offset().x(0).y(0);
            rpBeginInfo.renderArea().extent().width(w).height(h);

            vkCmdBeginRenderPass(cmdBuf, rpBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            renderPassActive = true;
            activeRenderPassHandle = rpHandle;

            // Negative height + y=height flips the Vulkan Y-axis to match OpenGL conventions.
            var viewport = VkViewport.calloc(1, stack)
                    .x(0f)
                    .y((float) h)
                    .width((float) w)
                    .height(-(float) h)
                    .minDepth(0f)
                    .maxDepth(1f);
            vkCmdSetViewport(cmdBuf, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack);
            scissor.offset().x(0).y(0);
            scissor.extent().width(w).height(h);
            vkCmdSetScissor(cmdBuf, 0, scissor);
        }
    }

    private void resetFrameState() {
        frameInProgress = false;
        renderPassActive = false;
        activeRenderTarget = null;
        activeRenderPassHandle = VK_NULL_HANDLE;
        activeRenderPassOptions = RenderPassOptions.clearColor();
    }

    private void recreateSwapChain() {
        waitForInFlightFrames();
        var oldFormat = swapChain.imageFormat();
        var oldDepthFormat = swapChain.depthFormat();
        var oldImageCount = swapChain.imageCount();
        swapChain.recreate(vSync);
        if (swapChain.imageFormat() != oldFormat || swapChain.depthFormat() != oldDepthFormat) {
            rebuildRenderPassCache();
        }
        if (swapChain.imageCount() != oldImageCount) {
            syncObjects.dispose();
            syncObjects = new VulkanSyncObjects(device, swapChain.imageCount());
            log.debug("Sync objects recreated for new image count {}", swapChain.imageCount());
        }
        swapchainOutOfDate = false;
        log.debug(
                "Swap chain recreated ({}x{})",
                swapChain.extent().width(),
                swapChain.extent().height());
    }

    private void rebuildRenderPassCache() {
        for (var renderPass : renderPassCache.values()) {
            renderPass.dispose();
        }
        renderPassCache.clear();
        invalidateGraphicsPipelines();
        log.debug("Render pass cache rebuilt for swapchain format {}", swapChain.imageFormat());
    }

    private void invalidateGraphicsPipelines() {
        for (var shader : liveShaders) {
            shader.invalidatePipeline();
        }
    }

    void waitForInFlightFrames() {
        if (syncObjects != null) {
            syncObjects.waitForAllFences();
            flushAllDeferredReleases();
        }
    }

    void deferRelease(Runnable releaseAction) {
        if (disposed || device == null || syncObjects == null) {
            releaseAction.run();
            return;
        }
        deferredReleases[currentFrame].add(releaseAction);
    }

    /**
     * Creates the global VkDescriptorSetLayout: set=0, binding=0, combined image sampler. This layout
     * is shared between all VulkanShaders and VulkanTexture2D instances.
     */
    private void createTextureDescriptorSetLayout() {
        try (MemoryStack stack = stackPush()) {
            var binding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(binding);

            var pLayout = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(device.device(), layoutInfo, null, pLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture descriptor set layout");
            }
            textureDescriptorSetLayout = pLayout.get(0);
        }
        log.debug("Global texture descriptor set layout created");
    }

    private void createDescriptorPool() {
        descriptorPools.add(createNewDescriptorPool());
    }

    private long createNewDescriptorPool() {
        try (MemoryStack stack = stackPush()) {
            var poolSizes = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_TEXTURE_DESCRIPTORS);

            var poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(poolSizes)
                    .maxSets(MAX_TEXTURE_DESCRIPTORS);

            var pPool = stack.mallocLong(1);
            if (vkCreateDescriptorPool(device.device(), poolInfo, null, pPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool");
            }
            log.debug("Descriptor pool created (max {} texture sets)", MAX_TEXTURE_DESCRIPTORS);
            return pPool.get(0);
        }
    }

    private void logDeviceInfo() {
        try (MemoryStack stack = stackPush()) {
            var props = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(device.physicalDevice(), props);
            rendererInfo = props.deviceNameString() + " (Vulkan)";
            log.debug("GPU: {}", props.deviceNameString());
            log.debug(
                    "Vulkan API: {}.{}.{}",
                    VK_API_VERSION_MAJOR(props.apiVersion()),
                    VK_API_VERSION_MINOR(props.apiVersion()),
                    VK_API_VERSION_PATCH(props.apiVersion()));
        }
    }

    private void disposeInternal(boolean waitForDeviceIdle) {
        if (disposed) {
            return;
        }
        disposed = true;
        if (instance == this) {
            instance = null;
        }

        if (waitForDeviceIdle && device != null) {
            device.waitIdle();
        }
        flushAllDeferredReleases();

        if (syncObjects != null) {
            syncObjects.dispose();
            syncObjects = null;
        }
        if (commandPool != null) {
            commandPool.dispose();
            commandPool = null;
        }

        if (device != null) {
            for (var pool : descriptorPools) {
                vkDestroyDescriptorPool(device.device(), pool, null);
            }
            if (textureDescriptorSetLayout != VK_NULL_HANDLE) {
                vkDestroyDescriptorSetLayout(device.device(), textureDescriptorSetLayout, null);
                textureDescriptorSetLayout = VK_NULL_HANDLE;
            }
        }
        descriptorPools.clear();
        descriptorSetToPool.clear();

        for (var renderPass : renderPassCache.values()) {
            renderPass.dispose();
        }
        renderPassCache.clear();
        if (swapChain != null) {
            swapChain.dispose();
            swapChain = null;
        }
        if (allocator != null) {
            allocator.dispose();
            allocator = null;
        }
        if (device != null) {
            device.dispose();
            device = null;
        }

        if (surface != VK_NULL_HANDLE && vkInstance != null) {
            org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(vkInstance.handle(), surface, null);
            surface = VK_NULL_HANDLE;
        }
        if (vkInstance != null) {
            vkInstance.dispose();
            vkInstance = null;
        }

        frameInProgress = false;
        renderPassActive = false;
        activeRenderTarget = null;
        swapchainOutOfDate = false;
    }

    private VulkanRenderPass getOrCreateRenderPass(VulkanRenderPassSpec spec) {
        return renderPassCache.computeIfAbsent(spec, ignored -> new VulkanRenderPass(device, spec));
    }

    private VulkanRenderPassSpec backbufferPassSpec(RenderPassOptions options) {
        var clearDepth = options.colorLoadAction() == ColorLoadAction.CLEAR;
        return new VulkanRenderPassSpec(
                swapChain.imageFormat(),
                clearDepth ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                toVulkanLoadOp(options.colorLoadAction()),
                true,
                swapChain.depthFormat(),
                clearDepth ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK_ATTACHMENT_STORE_OP_DONT_CARE);
    }

    private VulkanRenderPassSpec renderTargetPassSpec(RenderPassOptions options) {
        return new VulkanRenderPassSpec(
                VulkanRenderTarget.FORMAT,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                toVulkanLoadOp(options.colorLoadAction()),
                false,
                VK_FORMAT_UNDEFINED,
                VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK_ATTACHMENT_STORE_OP_DONT_CARE);
    }

    private int toVulkanLoadOp(ColorLoadAction loadAction) {
        return switch (loadAction) {
            case CLEAR -> VK_ATTACHMENT_LOAD_OP_CLEAR;
            case LOAD -> VK_ATTACHMENT_LOAD_OP_LOAD;
        };
    }

    private void flushDeferredReleases(int frameIndex) {
        var releases = deferredReleases[frameIndex];
        if (releases.isEmpty()) {
            return;
        }
        var pending = List.copyOf(releases);
        releases.clear();
        for (var release : pending) {
            release.run();
        }
    }

    private void flushAllDeferredReleases() {
        for (int i = 0; i < deferredReleases.length; i++) {
            flushDeferredReleases(i);
        }
    }

    @Override
    public String getRendererInfo() {
        return rendererInfo;
    }
}
