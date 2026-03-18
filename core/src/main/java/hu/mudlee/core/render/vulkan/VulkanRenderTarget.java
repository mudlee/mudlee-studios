package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.RenderTarget;
import hu.mudlee.core.render.texture.Texture2D;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan off-screen render target.
 *
 * <p>Creates a device-local VkImage that can be rendered into via its own VkRenderPass and
 * VkFramebuffer, then sampled as a texture (via a pre-written descriptor set) once the render pass
 * has ended.
 *
 * <p>The render pass transitions the image from SHADER_READ_ONLY_OPTIMAL (initial) to
 * COLOR_ATTACHMENT_OPTIMAL (during rendering) and back to SHADER_READ_ONLY_OPTIMAL (final) so that
 * it is always in the correct layout for sampling between frames.
 */
public final class VulkanRenderTarget extends RenderTarget {

    private static final Logger log = LoggerFactory.getLogger(VulkanRenderTarget.class);
    static final int FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    private final VulkanContext context;
    private final VulkanDevice device;
    private int width;
    private int height;

    private long image = VK_NULL_HANDLE;
    private long imageAllocation = VK_NULL_HANDLE;
    private long imageView = VK_NULL_HANDLE;
    private long sampler = VK_NULL_HANDLE;
    private long renderPassHandleField = VK_NULL_HANDLE;
    private long framebufferHandleField = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private VkExtent2D extentField;

    private final ColorTexture colorTexture = new ColorTexture();

    public VulkanRenderTarget(int width, int height) {
        this.context = VulkanContext.get();
        this.device = context.device();
        this.width = width;
        this.height = height;
        this.extentField = VkExtent2D.malloc().set(width, height);
        create(context);
        log.debug("VulkanRenderTarget created ({}x{})", width, height);
    }

    // TODO: what are these?
    long renderPassHandle() {
        return renderPassHandleField;
    }

    long framebufferHandle() {
        return framebufferHandleField;
    }

    VkExtent2D extent() {
        return extentField;
    }

    int vkWidth() {
        return width;
    }

    int vkHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Texture2D getColorTexture() {
        return colorTexture;
    }

    @Override
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = newWidth;
        height = newHeight;
        extentField.set(width, height);
        context.waitForInFlightFrames();
        destroyGpuObjects();
        create(context);
        log.debug("VulkanRenderTarget resized ({}x{})", width, height);
    }

    @Override
    public void dispose() {
        if (context.isDisposed()) {
            descriptorSet = VK_NULL_HANDLE;
            framebufferHandleField = VK_NULL_HANDLE;
            renderPassHandleField = VK_NULL_HANDLE;
            sampler = VK_NULL_HANDLE;
            imageView = VK_NULL_HANDLE;
            image = VK_NULL_HANDLE;
            imageAllocation = VK_NULL_HANDLE;
            extentField.free();
            log.debug("VulkanRenderTarget disposed after context shutdown");
            return;
        }

        context.waitForInFlightFrames();
        destroyGpuObjects();
        extentField.free();
        log.debug("VulkanRenderTarget disposed");
    }

    private void create(VulkanContext ctx) {
        createImage(ctx);
        transitionToShaderReadOnly(ctx.commandPool());
        createImageView();
        createRenderPass();
        createFramebuffer();
        createSampler();
        allocateAndWriteDescriptorSet(ctx);
    }

    private void destroyGpuObjects() {
        context.freeTextureDescriptorSet(descriptorSet);
        descriptorSet = VK_NULL_HANDLE;

        if (framebufferHandleField != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(device.device(), framebufferHandleField, null);
            framebufferHandleField = VK_NULL_HANDLE;
        }
        if (renderPassHandleField != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device.device(), renderPassHandleField, null);
            renderPassHandleField = VK_NULL_HANDLE;
        }
        if (sampler != VK_NULL_HANDLE) {
            vkDestroySampler(device.device(), sampler, null);
            sampler = VK_NULL_HANDLE;
        }
        if (imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device.device(), imageView, null);
            imageView = VK_NULL_HANDLE;
        }
        if (image != VK_NULL_HANDLE) {
            vmaDestroyImage(context.allocator().handle(), image, imageAllocation);
            image = VK_NULL_HANDLE;
            imageAllocation = VK_NULL_HANDLE;
        }
    }

    private void createImage(VulkanContext ctx) {
        try (MemoryStack stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(FORMAT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.extent().width(width).height(height).depth(1);

            var allocInfo = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);

            var pImage = stack.mallocLong(1);
            var pAlloc = stack.mallocPointer(1);
            if (vmaCreateImage(ctx.allocator().handle(), imageInfo, allocInfo, pImage, pAlloc, null) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkImage for render target");
            }
            image = pImage.get(0);
            imageAllocation = pAlloc.get(0);
        }
    }

    private void transitionToShaderReadOnly(VulkanCommandPool commandPool) {
        try (MemoryStack stack = stackPush()) {
            var cmdBuf = commandPool.beginSingleUse(stack);

            var barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            barrier.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            vkCmdPipelineBarrier(
                    cmdBuf,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    barrier);
            commandPool.endSingleUse(cmdBuf);
        }
    }

    private void createImageView() {
        try (MemoryStack stack = stackPush()) {
            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(FORMAT);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            var pView = stack.mallocLong(1);
            if (vkCreateImageView(device.device(), viewInfo, null, pView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkImageView for render target");
            }
            imageView = pView.get(0);
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            var colorAttachment = VkAttachmentDescription.calloc(1, stack)
                    .format(FORMAT)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            var colorRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef);

            // Dep 1: wait for any previous fragment shader reads before writing to the attachment
            // Dep 2: make the color writes visible to the next fragment shader reads
            var deps = VkSubpassDependency.calloc(2, stack);
            deps.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            deps.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            var rpInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment)
                    .pSubpasses(subpass)
                    .pDependencies(deps);

            var pRp = stack.mallocLong(1);
            if (vkCreateRenderPass(device.device(), rpInfo, null, pRp) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass for VulkanRenderTarget");
            }
            renderPassHandleField = pRp.get(0);
        }
    }

    private void createFramebuffer() {
        try (MemoryStack stack = stackPush()) {
            var fbInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPassHandleField)
                    .attachmentCount(1)
                    .pAttachments(stack.longs(imageView))
                    .width(width)
                    .height(height)
                    .layers(1);

            var pFb = stack.mallocLong(1);
            if (vkCreateFramebuffer(device.device(), fbInfo, null, pFb) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkFramebuffer for render target");
            }
            framebufferHandleField = pFb.get(0);
        }
    }

    private void createSampler() {
        try (MemoryStack stack = stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .mipLodBias(0f)
                    .minLod(0f)
                    .maxLod(0f);

            var pSampler = stack.mallocLong(1);
            if (vkCreateSampler(device.device(), samplerInfo, null, pSampler) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkSampler for render target");
            }
            sampler = pSampler.get(0);
        }
    }

    private void allocateAndWriteDescriptorSet(VulkanContext ctx) {
        descriptorSet = ctx.allocateTextureDescriptorSet();

        try (MemoryStack stack = stackPush()) {
            var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(imageView)
                    .sampler(sampler);

            var write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device.device(), write, null);
        }
    }

    /** Non-owning Texture2D view over this render target's color attachment. */
    private final class ColorTexture extends Texture2D {

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public void bind() {
            if (!context.isDisposed()) {
                context.setActiveDescriptorSet(descriptorSet);
            }
        }

        @Override
        public void dispose() {
            // Owned by VulkanRenderTarget — call VulkanRenderTarget.dispose() instead
        }
    }
}
