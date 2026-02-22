package hu.mudlee.core.render.vulkan;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK12.*;

/**
 * A single-subpass render pass with one color attachment.
 * Clear on load, present-ready layout on store — matches the standard frame rendering pattern.
 */
class VulkanRenderPass implements Disposable {

  private static final Logger log = LoggerFactory.getLogger(VulkanRenderPass.class);

  private final VulkanDevice device;
  private final long handle;

  VulkanRenderPass(VulkanDevice device, int colorFormat) {
    this.device = device;

    try (MemoryStack stack = stackPush()) {
      // Describe the single color attachment (swapchain image)
      VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack)
        .format(colorFormat)
        .samples(VK_SAMPLE_COUNT_1_BIT)
        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)       // Clear to clearColor at render pass start
        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)     // Keep contents for presentation
        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
        .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

      VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
        .attachment(0)
        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

      VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
        .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
        .colorAttachmentCount(1)
        .pColorAttachments(colorRef);

      // Subpass dependency: ensure the image is available before writing to it
      VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
        .srcSubpass(VK_SUBPASS_EXTERNAL)
        .dstSubpass(0)
        .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        .srcAccessMask(0)
        .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

      VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
        .pAttachments(colorAttachment)
        .pSubpasses(subpass)
        .pDependencies(dependency);

      LongBuffer pRenderPass = stack.mallocLong(1);
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

  @Override
  public void dispose() {
    vkDestroyRenderPass(device.device(), handle, null);
    log.debug("VkRenderPass destroyed");
  }
}
