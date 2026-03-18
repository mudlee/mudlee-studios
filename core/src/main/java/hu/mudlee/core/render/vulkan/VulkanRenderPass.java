package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single-subpass render pass with one color attachment.
 */
class VulkanRenderPass implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanRenderPass.class);

    private final VulkanDevice device;
    private final long handle;
    private final VulkanRenderPassSpec spec;

    VulkanRenderPass(VulkanDevice device, VulkanRenderPassSpec spec) {
        this.device = device;
        this.spec = spec;

        try (MemoryStack stack = stackPush()) {
            var colorAttachment = VkAttachmentDescription.calloc(1, stack)
                    .format(spec.colorFormat())
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(spec.colorLoadOp())
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(spec.initialLayout())
                    .finalLayout(spec.finalLayout());

            var colorRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef);

            var dependencies = createDependencies(stack);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment)
                    .pSubpasses(subpass)
                    .pDependencies(dependencies);

            var pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device.device(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkRenderPass");
            }

            handle = pRenderPass.get(0);
            log.debug("VkRenderPass created");
        }
    }

    private VkSubpassDependency.Buffer createDependencies(MemoryStack stack) {
        if (spec.initialLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                && spec.finalLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            var dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies
                    .get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            dependencies
                    .get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            return dependencies;
        }

        return VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
    }

    long handle() {
        return handle;
    }

    VulkanRenderPassSpec spec() {
        return spec;
    }

    @Override
    public void dispose() {
        vkDestroyRenderPass(device.device(), handle, null);
        log.debug("VkRenderPass destroyed");
    }
}
