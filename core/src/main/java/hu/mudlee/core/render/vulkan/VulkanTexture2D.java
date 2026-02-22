package hu.mudlee.core.render.vulkan;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.texture.Texture2D;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan texture implementation: VkImage + VkDeviceMemory + VkImageView + VkSampler.
 *
 * <p>Upload strategy: staging buffer → device-local VkImage, then two layout transitions (UNDEFINED
 * → TRANSFER_DST_OPTIMAL → SHADER_READ_ONLY_OPTIMAL) via single-use command buffers.
 *
 * <p>STBImage is loaded with forced RGBA (4 channels) so we always use VK_FORMAT_R8G8B8A8_SRGB.
 *
 * <p>bind() registers this texture as the active texture on VulkanContext so that
 * VulkanContext.renderRaw() can bind the correct descriptor set.
 */
public class VulkanTexture2D extends Texture2D {

  private static final Logger log = LoggerFactory.getLogger(VulkanTexture2D.class);

  private final VulkanDevice device;
  private final String path;

  private long image = VK_NULL_HANDLE;
  private long imageMemory = VK_NULL_HANDLE;
  private long imageView = VK_NULL_HANDLE;
  private long sampler = VK_NULL_HANDLE;
  private long descriptorSet = VK_NULL_HANDLE;

  public VulkanTexture2D(String path) {
    this.path = path;
    var ctx = VulkanContext.get();
    this.device = ctx.device();

    uploadTexture(ctx);
    createImageView();
    createSampler();
    allocateAndWriteDescriptorSet(ctx);

    log.debug("VulkanTexture2D created: {}", path);
  }

  /** Informs VulkanContext that this is the texture to bind for the next draw call(s). */
  @Override
  public void bind() {
    VulkanContext.get().setActiveTexture(this);
  }

  @Override
  public void unBind() {
    // No-op: Vulkan textures are unbound implicitly by the next descriptor set bind
  }

  long descriptorSet() {
    return descriptorSet;
  }

  public void dispose() {
    if (sampler != VK_NULL_HANDLE) {
      vkDestroySampler(device.device(), sampler, null);
    }
    if (imageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device.device(), imageView, null);
    }
    if (image != VK_NULL_HANDLE) {
      vkDestroyImage(device.device(), image, null);
    }
    if (imageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device.device(), imageMemory, null);
    }
    log.debug("VulkanTexture2D disposed: {}", path);
  }

  // -------------------------------------------------------------------------
  // Texture upload
  // -------------------------------------------------------------------------

  private void uploadTexture(VulkanContext ctx) {
    try (MemoryStack stack = stackPush()) {
      // Force RGBA output from STBImage — Vulkan prefers a consistent 4-channel format
      var w = stack.mallocInt(1);
      var h = stack.mallocInt(1);
      var channels = stack.mallocInt(1);

      var imageData = ResourceLoader.loadToByteBuffer(path, stack);
      var pixels = stbi_load_from_memory(imageData, w, h, channels, STBI_rgb_alpha);
      if (pixels == null) {
        throw new RuntimeException(
            "Failed to load texture '" + path + "': " + stbi_failure_reason());
      }

      var width = w.get(0);
      var height = h.get(0);
      var imageSizeBytes = (long) width * height * 4; // RGBA = 4 bytes per pixel

      // Staging buffer: CPU-writable
      var staging =
          new VulkanBuffer(
              device,
              imageSizeBytes,
              VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

      staging.map(
          dst -> {
            dst.put(pixels);
            dst.flip();
          });

      // rewind() resets position to 0 so that memAddress() resolves to the base allocation address
      stbi_image_free(pixels.rewind());

      // Create the device-local VkImage
      createImage(
          width,
          height,
          VK_FORMAT_R8G8B8A8_SRGB,
          VK_IMAGE_TILING_OPTIMAL,
          VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
          VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

      // Transition: UNDEFINED → TRANSFER_DST_OPTIMAL, copy pixels, then SHADER_READ_ONLY
      transitionImageLayout(
          ctx.commandPool(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

      copyBufferToImage(staging, width, height, ctx.commandPool());

      transitionImageLayout(
          ctx.commandPool(),
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

      staging.dispose();
    }
  }

  private void createImage(
      int width, int height, int format, int tiling, int usage, int memoryProps) {
    try (MemoryStack stack = stackPush()) {
      var imageInfo =
          VkImageCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
              .imageType(VK_IMAGE_TYPE_2D)
              .mipLevels(1)
              .arrayLayers(1)
              .format(format)
              .tiling(tiling)
              .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
              .usage(usage)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
              .samples(VK_SAMPLE_COUNT_1_BIT);
      imageInfo.extent().width(width).height(height).depth(1);

      var pImage = stack.mallocLong(1);
      if (vkCreateImage(device.device(), imageInfo, null, pImage) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create VkImage for texture '" + path + "'");
      }
      image = pImage.get(0);

      var memReqs = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device.device(), image, memReqs);

      var allocInfo =
          VkMemoryAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
              .allocationSize(memReqs.size())
              .memoryTypeIndex(
                  VulkanMemoryUtil.findMemoryType(
                      device.memoryProperties(), memReqs.memoryTypeBits(), memoryProps));

      var pMemory = stack.mallocLong(1);
      if (vkAllocateMemory(device.device(), allocInfo, null, pMemory) != VK_SUCCESS) {
        throw new RuntimeException("Failed to allocate image memory for '" + path + "'");
      }
      imageMemory = pMemory.get(0);

      vkBindImageMemory(device.device(), image, imageMemory, 0);
    }
  }

  private void transitionImageLayout(VulkanCommandPool commandPool, int oldLayout, int newLayout) {
    try (MemoryStack stack = stackPush()) {
      var cmdBuf = commandPool.beginSingleUse(stack);

      var barrier =
          VkImageMemoryBarrier.calloc(1, stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
              .oldLayout(oldLayout)
              .newLayout(newLayout)
              .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
              .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
              .image(image);
      barrier
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);

      int srcStage, dstStage;

      if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED
          && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask(0);
        barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
      } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
          && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
      } else {
        throw new RuntimeException(
            "Unsupported image layout transition: " + oldLayout + " → " + newLayout);
      }

      vkCmdPipelineBarrier(cmdBuf, srcStage, dstStage, 0, null, null, barrier);
      commandPool.endSingleUse(cmdBuf);
    }
  }

  private void copyBufferToImage(
      VulkanBuffer buffer, int width, int height, VulkanCommandPool commandPool) {
    try (MemoryStack stack = stackPush()) {
      var cmdBuf = commandPool.beginSingleUse(stack);

      var region =
          VkBufferImageCopy.calloc(1, stack)
              .bufferOffset(0)
              .bufferRowLength(0)
              .bufferImageHeight(0);
      region
          .imageSubresource()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .mipLevel(0)
          .baseArrayLayer(0)
          .layerCount(1);
      region.imageOffset().x(0).y(0).z(0);
      region.imageExtent().width(width).height(height).depth(1);

      vkCmdCopyBufferToImage(
          cmdBuf, buffer.handle(), image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

      commandPool.endSingleUse(cmdBuf);
    }
  }

  private void createImageView() {
    try (MemoryStack stack = stackPush()) {
      var viewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
              .image(image)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_SRGB);
      viewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);

      var pView = stack.mallocLong(1);
      if (vkCreateImageView(device.device(), viewInfo, null, pView) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create VkImageView for '" + path + "'");
      }
      imageView = pView.get(0);
    }
  }

  private void createSampler() {
    try (MemoryStack stack = stackPush()) {
      // Nearest filtering matches the OpenGL GL_NEAREST behaviour used for pixel art
      var samplerInfo =
          VkSamplerCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
              .magFilter(VK_FILTER_NEAREST)
              .minFilter(VK_FILTER_NEAREST)
              .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .anisotropyEnable(false)
              .maxAnisotropy(1.0f)
              .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
              .unnormalizedCoordinates(false)
              .compareEnable(false)
              .compareOp(VK_COMPARE_OP_ALWAYS)
              .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
              .mipLodBias(0.0f)
              .minLod(0.0f)
              .maxLod(0.0f);

      var pSampler = stack.mallocLong(1);
      if (vkCreateSampler(device.device(), samplerInfo, null, pSampler) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create VkSampler for '" + path + "'");
      }
      sampler = pSampler.get(0);
    }
  }

  /**
   * Allocates a descriptor set from the shared pool in VulkanContext and writes this texture's
   * image view + sampler into it.
   */
  private void allocateAndWriteDescriptorSet(VulkanContext ctx) {
    // Allocates from the shared pool using the global layout owned by VulkanContext
    descriptorSet = ctx.allocateTextureDescriptorSet();

    try (MemoryStack stack = stackPush()) {
      var imageInfo =
          VkDescriptorImageInfo.calloc(1, stack)
              .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
              .imageView(imageView)
              .sampler(sampler);

      var descriptorWrite =
          VkWriteDescriptorSet.calloc(1, stack)
              .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
              .dstSet(descriptorSet)
              .dstBinding(0)
              .dstArrayElement(0)
              .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
              .descriptorCount(1)
              .pImageInfo(imageInfo);

      vkUpdateDescriptorSets(device.device(), descriptorWrite, null);
    }
  }
}
