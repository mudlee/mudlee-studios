package hu.mudlee.core.render.vulkan;

import static hu.mudlee.core.render.vulkan.VulkanCommandPool.FRAMES_IN_FLIGHT;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.GraphicsContext;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
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
 * <p>clear() → wait fence → acquire swap chain image → begin command buffer → begin render pass
 * renderRaw() → bind pipeline → push constants → bind texture descriptor set → draw swapBuffers()→
 * end render pass → end command buffer → submit → present → advance frame index
 *
 * <p>Window resize is handled lazily: swapchainOutOfDate is set on windowResized() and the swap
 * chain is recreated at the next clear() call.
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
  private VulkanRenderPass renderPass;
  private VulkanCommandPool commandPool;
  private VulkanSyncObjects syncObjects;

  // Global descriptor layout for combined-image-sampler at set=0, binding=0
  private long textureDescriptorSetLayout = VK_NULL_HANDLE;
  // Shared pool from which VulkanTexture2D allocates its descriptor sets
  private long descriptorPool = VK_NULL_HANDLE;

  // Frame state
  private int currentFrame = 0;
  private int currentImageIndex = 0;
  private boolean swapchainOutOfDate = false;
  private boolean vSync = true;
  private long windowId = 0;

  private final float[] clearColor = {0f, 0f, 0f, 1f};
  private VulkanTexture2D activeTexture;

  public VulkanContext(boolean debug) {
    this.debug = debug;
    instance = this;
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

  VulkanDevice device() {
    return device;
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

  long renderPassHandle() {
    return renderPass.handle();
  }

  VkExtent2D swapChainExtent() {
    return swapChain.extent();
  }

  void setActiveTexture(VulkanTexture2D texture) {
    activeTexture = texture;
  }

  /**
   * Allocates a single descriptor set from the shared pool using the global texture layout. Called
   * by VulkanTexture2D during construction.
   */
  long allocateTextureDescriptorSet() {
    try (MemoryStack stack = stackPush()) {
      LongBuffer pLayout = stack.longs(textureDescriptorSetLayout);

      VkDescriptorSetAllocateInfo allocInfo =
          VkDescriptorSetAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
              .descriptorPool(descriptorPool)
              .pSetLayouts(pLayout);

      LongBuffer pDescriptorSet = stack.mallocLong(1);
      if (vkAllocateDescriptorSets(device.device(), allocInfo, pDescriptorSet) != VK_SUCCESS) {
        throw new RuntimeException("Failed to allocate texture descriptor set");
      }
      return pDescriptorSet.get(0);
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

    vkInstance = new VulkanInstance("MudleeEngine", debug);

    try (MemoryStack stack = stackPush()) {
      LongBuffer pSurface = stack.mallocLong(1);
      int result = glfwCreateWindowSurface(vkInstance.handle(), windowId, null, pSurface);
      if (result != VK_SUCCESS) {
        throw new RuntimeException("Failed to create Vulkan window surface: " + result);
      }
      surface = pSurface.get(0);
    }

    device = new VulkanDevice(vkInstance.handle(), surface);
    swapChain = new VulkanSwapChain(device, surface, windowId, vSync);
    renderPass = new VulkanRenderPass(device, swapChain.imageFormat());
    swapChain.buildFramebuffers(renderPass.handle());
    commandPool = new VulkanCommandPool(device);
    syncObjects = new VulkanSyncObjects(device, swapChain.imageCount());
    createTextureDescriptorSetLayout();
    createDescriptorPool();

    logDeviceInfo();
    log.debug("Vulkan context ready. vSync={}", vSync);
  }

  @Override
  public void setClearColor(Vector4f color) {
    clearColor[0] = color.x;
    clearColor[1] = color.y;
    clearColor[2] = color.z;
    clearColor[3] = color.w;
  }

  @Override
  public void setClearFlags(int mask) {
    // No-op: Vulkan clearing is declared as a render pass load op, not a separate call
  }

  /**
   * Begins a new frame: – Waits for this frame slot's GPU fence (CPU/GPU sync). – Acquires the next
   * swap chain image. – Resets and begins recording the command buffer. – Starts the render pass
   * with the stored clear colour. – Sets dynamic viewport and scissor.
   */
  @Override
  public void clear() {
    if (swapchainOutOfDate) {
      recreateSwapChain();
    }

    try (MemoryStack stack = stackPush()) {
      long fence = syncObjects.inFlightFence(currentFrame);
      vkWaitForFences(device.device(), fence, true, Long.MAX_VALUE);

      IntBuffer pImageIndex = stack.mallocInt(1);
      int result =
          vkAcquireNextImageKHR(
              device.device(),
              swapChain.swapChainHandle(),
              Long.MAX_VALUE,
              syncObjects.imageAvailableSemaphore(currentFrame),
              VK_NULL_HANDLE,
              pImageIndex);

      if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapChain();
        return;
      } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        throw new RuntimeException("Failed to acquire swap chain image: " + result);
      }

      currentImageIndex = pImageIndex.get(0);

      // Reset fence only after a successful acquisition to avoid deadlocks on resize
      vkResetFences(device.device(), fence);

      VkCommandBuffer cmdBuf = commandPool.commandBuffer(currentFrame);
      vkResetCommandBuffer(cmdBuf, 0);

      VkCommandBufferBeginInfo beginInfo =
          VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
      if (vkBeginCommandBuffer(cmdBuf, beginInfo) != VK_SUCCESS) {
        throw new RuntimeException("Failed to begin command buffer");
      }

      VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
      clearValues
          .get(0)
          .color()
          .float32(0, clearColor[0])
          .float32(1, clearColor[1])
          .float32(2, clearColor[2])
          .float32(3, clearColor[3]);

      VkRenderPassBeginInfo rpBeginInfo =
          VkRenderPassBeginInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
              .renderPass(renderPass.handle())
              .framebuffer(swapChain.framebuffer(currentImageIndex))
              .pClearValues(clearValues);
      rpBeginInfo.renderArea().offset().x(0).y(0);
      rpBeginInfo
          .renderArea()
          .extent()
          .width(swapChain.extent().width())
          .height(swapChain.extent().height());

      vkCmdBeginRenderPass(cmdBuf, rpBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

      // Negative height + y=height flips the Vulkan Y-axis to match OpenGL conventions.
      // JOML's setOrtho produces matrices expecting Y-up (OpenGL), so we compensate here.
      VkViewport.Buffer viewport =
          VkViewport.calloc(1, stack)
              .x(0f)
              .y((float) swapChain.extent().height())
              .width((float) swapChain.extent().width())
              .height(-(float) swapChain.extent().height())
              .minDepth(0f)
              .maxDepth(1f);
      vkCmdSetViewport(cmdBuf, 0, viewport);

      VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
      scissor.offset().x(0).y(0);
      scissor.extent().width(swapChain.extent().width()).height(swapChain.extent().height());
      vkCmdSetScissor(cmdBuf, 0, scissor);
    }
  }

  /**
   * Records a draw call into the current command buffer. clear() must have been called earlier in
   * the same frame.
   *
   * <p>Per Vulkan best practice: – Matrices pushed as push constants (no per-frame UBO allocation).
   * – Texture bound via a pre-built descriptor set (written at texture creation time).
   */
  @Override
  public void renderRaw(
      VertexArray vertexArray, Shader shader, RenderMode renderMode, PolygonMode polygonMode) {
    if (!(shader instanceof VulkanShader vs)) {
      throw new IllegalArgumentException("VulkanContext requires a VulkanShader");
    }
    if (!(vertexArray instanceof VulkanVertexArray va)) {
      throw new IllegalArgumentException("VulkanContext requires a VulkanVertexArray");
    }
    if (va.getVBOs().isEmpty()) return;

    VkCommandBuffer cmdBuf = commandPool.commandBuffer(currentFrame);

    // Create the VkPipeline lazily with the actual vertex layout (now known)
    VertexBuffer firstVbo = va.getVBOs().get(0);
    long pipeline =
        vs.getOrCreatePipeline(firstVbo.getLayout(), renderPass.handle(), swapChain.extent());

    vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

    try (MemoryStack stack = stackPush()) {
      // Push constants: projection (offset 0) + view (offset 64) — both mat4
      var pushData = stack.mallocFloat(32);
      pushData.put(vs.projectionData()).put(vs.viewData()).flip();
      vkCmdPushConstants(cmdBuf, vs.pipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushData);

      // Bind texture descriptor set (set=0)
      if (activeTexture != null) {
        LongBuffer pSet = stack.longs(activeTexture.descriptorSet());
        vkCmdBindDescriptorSets(
            cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, vs.pipelineLayout(), 0, pSet, null);
      }

      // Bind vertex buffers
      int vboCount = va.getVBOs().size();
      LongBuffer pBuffers = stack.mallocLong(vboCount);
      LongBuffer pOffsets = stack.callocLong(vboCount);
      for (VertexBuffer vb : va.getVBOs()) {
        pBuffers.put(((VulkanVertexBuffer) vb).bufferHandle());
      }
      pBuffers.flip();
      vkCmdBindVertexBuffers(cmdBuf, 0, pBuffers, pOffsets);

      // Draw indexed or non-indexed
      if (va.getEBO().isPresent() && va.getEBO().get() instanceof VulkanIndexBuffer ib) {
        vkCmdBindIndexBuffer(cmdBuf, ib.bufferHandle(), 0, VK_INDEX_TYPE_UINT32);
        int instanceCount = va.isInstanced() ? va.getInstanceCount() : 1;
        vkCmdDrawIndexed(cmdBuf, ib.getLength(), instanceCount, 0, 0, 0);
      } else {
        // Derive vertex count from buffer length and stride
        int stride =
            (firstVbo.getLayout().attributes().length > 0)
                ? firstVbo.getLayout().attributes()[0].getStride()
                : Float.BYTES;
        int vertexCount = (firstVbo.getLength() * Float.BYTES) / stride;
        int instanceCount = va.isInstanced() ? va.getInstanceCount() : 1;
        vkCmdDraw(cmdBuf, vertexCount, instanceCount, 0, 0);
      }
    }
  }

  /** Ends the render pass, submits the command buffer to the graphics queue, and presents. */
  @Override
  public void swapBuffers(float frameTime) {
    try (MemoryStack stack = stackPush()) {
      VkCommandBuffer cmdBuf = commandPool.commandBuffer(currentFrame);

      vkCmdEndRenderPass(cmdBuf);
      if (vkEndCommandBuffer(cmdBuf) != VK_SUCCESS) {
        throw new RuntimeException("Failed to end command buffer");
      }

      VkSubmitInfo submitInfo =
          VkSubmitInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
              .waitSemaphoreCount(1)
              .pWaitSemaphores(stack.longs(syncObjects.imageAvailableSemaphore(currentFrame)))
              .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
              .pCommandBuffers(stack.pointers(cmdBuf))
              .pSignalSemaphores(
                  stack.longs(syncObjects.renderFinishedSemaphore(currentImageIndex)));

      int result =
          vkQueueSubmit(
              device.graphicsQueue(), submitInfo, syncObjects.inFlightFence(currentFrame));
      if (result != VK_SUCCESS) {
        throw new RuntimeException("Failed to submit command buffer: " + result);
      }

      VkPresentInfoKHR presentInfo =
          VkPresentInfoKHR.calloc(stack)
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
    device.waitIdle();

    syncObjects.dispose();
    commandPool.dispose();

    if (descriptorPool != VK_NULL_HANDLE) {
      vkDestroyDescriptorPool(device.device(), descriptorPool, null);
    }
    if (textureDescriptorSetLayout != VK_NULL_HANDLE) {
      vkDestroyDescriptorSetLayout(device.device(), textureDescriptorSetLayout, null);
    }

    renderPass.dispose();
    swapChain.dispose();
    device.dispose();

    if (surface != VK_NULL_HANDLE) {
      org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(vkInstance.handle(), surface, null);
    }
    vkInstance.dispose();

    log.debug("VulkanContext disposed");
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private void recreateSwapChain() {
    device.waitIdle();
    swapChain.recreate(renderPass.handle(), vSync);
    swapchainOutOfDate = false;
    log.debug(
        "Swap chain recreated ({}x{})", swapChain.extent().width(), swapChain.extent().height());
  }

  /**
   * Creates the global VkDescriptorSetLayout: set=0, binding=0, combined image sampler. This layout
   * is shared between all VulkanShaders and VulkanTexture2D instances.
   */
  private void createTextureDescriptorSetLayout() {
    try (MemoryStack stack = stackPush()) {
      VkDescriptorSetLayoutBinding.Buffer binding =
          VkDescriptorSetLayoutBinding.calloc(1, stack)
              .binding(0)
              .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
              .descriptorCount(1)
              .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

      VkDescriptorSetLayoutCreateInfo layoutInfo =
          VkDescriptorSetLayoutCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
              .pBindings(binding);

      LongBuffer pLayout = stack.mallocLong(1);
      if (vkCreateDescriptorSetLayout(device.device(), layoutInfo, null, pLayout) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create texture descriptor set layout");
      }
      textureDescriptorSetLayout = pLayout.get(0);
    }
    log.debug("Global texture descriptor set layout created");
  }

  private void createDescriptorPool() {
    try (MemoryStack stack = stackPush()) {
      VkDescriptorPoolSize.Buffer poolSizes =
          VkDescriptorPoolSize.calloc(1, stack)
              .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
              .descriptorCount(MAX_TEXTURE_DESCRIPTORS);

      VkDescriptorPoolCreateInfo poolInfo =
          VkDescriptorPoolCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
              .pPoolSizes(poolSizes)
              .maxSets(MAX_TEXTURE_DESCRIPTORS);

      LongBuffer pPool = stack.mallocLong(1);
      if (vkCreateDescriptorPool(device.device(), poolInfo, null, pPool) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create descriptor pool");
      }
      descriptorPool = pPool.get(0);
    }
    log.debug("Descriptor pool created (max {} texture sets)", MAX_TEXTURE_DESCRIPTORS);
  }

  private void logDeviceInfo() {
    try (MemoryStack stack = stackPush()) {
      VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
      vkGetPhysicalDeviceProperties(device.physicalDevice(), props);
      log.debug("GPU: {}", props.deviceNameString());
      log.debug(
          "Vulkan API: {}.{}.{}",
          VK_API_VERSION_MAJOR(props.apiVersion()),
          VK_API_VERSION_MINOR(props.apiVersion()),
          VK_API_VERSION_PATCH(props.apiVersion()));
    }
  }
}
