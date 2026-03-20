package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A single-subpass render pass with a color attachment and optional depth attachment. */
class VulkanRenderPass implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanRenderPass.class);

    private final VulkanDevice device;
    private final long handle;
    private final VulkanRenderPassSpec spec;

    VulkanRenderPass(VulkanDevice device, VulkanRenderPassSpec spec) {
        this.device = device;
        this.spec = spec;

        try (MemoryStack stack = stackPush()) {
            var attachments = VkAttachmentDescription.calloc(spec.hasDepthAttachment() ? 2 : 1, stack);
            attachments
                    .get(0)
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

            VkAttachmentReference depthRef = null;
            if (spec.hasDepthAttachment()) {
                attachments
                        .get(1)
                        .format(spec.depthFormat())
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(spec.depthLoadOp())
                        .storeOp(spec.depthStoreOp())
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                        .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                depthRef = VkAttachmentReference.calloc(stack)
                        .attachment(1)
                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef)
                    .pDepthStencilAttachment(depthRef);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(createDependencies(stack));

            var pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device.device(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkRenderPass");
            }

            handle = pRenderPass.get(0);
            log.debug("VkRenderPass created");
        }
    }

    private VkSubpassDependency.Buffer createDependencies(MemoryStack stack) {
        var colorAndDepthStages = spec.hasDepthAttachment()
                ? VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        var colorAndDepthAccess = spec.hasDepthAttachment()
                ? VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        if (spec.initialLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                && spec.finalLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            var dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies
                    .get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstStageMask(colorAndDepthStages)
                    .dstAccessMask(colorAndDepthAccess)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            dependencies
                    .get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(colorAndDepthStages)
                    .srcAccessMask(colorAndDepthAccess)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            return dependencies;
        }

        return VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(colorAndDepthStages)
                .srcAccessMask(0)
                .dstStageMask(colorAndDepthStages)
                .dstAccessMask(colorAndDepthAccess);
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
