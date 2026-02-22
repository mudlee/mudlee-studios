package hu.mudlee.core.render.vulkan;

import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.ElementBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan index buffer backed by device-local GPU memory. Index type is always VK_INDEX_TYPE_UINT32
 * (matches int[] input).
 *
 * <p>bind()/unbind() are no-ops — index buffer binding is done explicitly via vkCmdBindIndexBuffer
 * inside VulkanContext.renderRaw().
 */
public class VulkanIndexBuffer extends ElementBuffer {

  private static final Logger log = LoggerFactory.getLogger(VulkanIndexBuffer.class);

  private final VulkanBuffer gpuBuffer;
  private final int length;

  /** Convenience constructor — resolves device and command pool from the active VulkanContext. */
  public VulkanIndexBuffer(int[] indices) {
    this(indices, VulkanContext.get().device(), VulkanContext.get().commandPool());
  }

  public VulkanIndexBuffer(int[] indices, VulkanDevice device, VulkanCommandPool commandPool) {
    this.length = indices.length;
    var sizeBytes = (long) indices.length * Integer.BYTES;

    var staging =
        new VulkanBuffer(
            device,
            sizeBytes,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    staging.map(
        dst -> {
          var intView = dst.asIntBuffer();
          intView.put(indices).flip();
        });

    gpuBuffer =
        new VulkanBuffer(
            device,
            sizeBytes,
            VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    gpuBuffer.copyFrom(staging, commandPool);
    staging.dispose();

    log.debug("VulkanIndexBuffer created ({} indices)", indices.length);
  }

  /** Returns the raw VkBuffer handle for use in vkCmdBindIndexBuffer. */
  long bufferHandle() {
    return gpuBuffer.handle();
  }

  @Override
  public int getId() {
    // Vulkan handles are long — return 0, use bufferHandle() instead
    return 0;
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public void bind() {
    // No-op: binding happens in vkCmdBindIndexBuffer during command recording
  }

  @Override
  public void unbind() {
    // No-op
  }

  @Override
  public void dispose() {
    gpuBuffer.dispose();
    log.debug("VulkanIndexBuffer disposed");
  }
}
