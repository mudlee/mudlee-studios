package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single-subpass render pass with one color attachment targeting the swapchain backbuffer.
 */
class VulkanRenderPass implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanRenderPass.class);

    private final VulkanDevice device;
    private final long handle;
    private final boolean clearsOnLoad;

    VulkanRenderPass(VulkanDevice device, int colorFormat) {
        this(device, colorFormat, true);
    }

    VulkanRenderPass(VulkanDevice device, int colorFormat, boolean clearsOnLoad) {
        this.device = device;
        this.clearsOnLoad = clearsOnLoad;

        try (MemoryStack stack = stackPush()) {
            // Describe the single color attachment (swapchain image)
            var colorAttachment = VkAttachmentDescription.calloc(1, stack)
                    .format(colorFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(clearsOnLoad ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE) // Keep contents for presentation
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(clearsOnLoad ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            var colorRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef);

            // Subpass dependency: ensure the image is available before writing to it
            var dependency = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);

            var pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device.device(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkRenderPass");
            }

            handle = pRenderPass.get(0);
            log.debug("VkRenderPass created");
        }
    }

    long handle() {
        return handle;
    }

    boolean clearsOnLoad() {
        return clearsOnLoad;
    }

    @Override
    public void dispose() {
        vkDestroyRenderPass(device.device(), handle, null);
        log.debug("VkRenderPass destroyed");
    }
}
