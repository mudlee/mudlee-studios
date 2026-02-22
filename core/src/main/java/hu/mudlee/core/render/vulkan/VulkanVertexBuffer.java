package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
import java.nio.FloatBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan vertex buffer backed by device-local GPU memory. Uses a host-visible staging buffer to
 * upload vertex data.
 *
 * <p>bind()/unbind() are no-ops — vertex buffer binding is done explicitly via
 * vkCmdBindVertexBuffers inside VulkanContext.renderRaw().
 */
public class VulkanVertexBuffer extends VertexBuffer {

  private static final Logger log = LoggerFactory.getLogger(VulkanVertexBuffer.class);

  private final VulkanBuffer gpuBuffer;
  private final VertexBufferLayout layout;
  private final int length;

  /** Convenience constructor — resolves device and command pool from the active VulkanContext. */
  public VulkanVertexBuffer(float[] vertices, VertexBufferLayout layout) {
    this(vertices, layout, VulkanContext.get().device(), VulkanContext.get().commandPool());
  }

  public VulkanVertexBuffer(
      float[] vertices,
      VertexBufferLayout layout,
      VulkanDevice device,
      VulkanCommandPool commandPool) {
    this.layout = layout;
    this.length = vertices.length;

    long sizeBytes = (long) vertices.length * Float.BYTES;

    // Stage: host-visible buffer for CPU upload
    VulkanBuffer staging =
        new VulkanBuffer(
            device,
            sizeBytes,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    staging.map(
        dst -> {
          FloatBuffer floatView = dst.asFloatBuffer();
          floatView.put(vertices).flip();
        });

    // Device-local: fast GPU memory, only accessible from the GPU
    gpuBuffer =
        new VulkanBuffer(
            device,
            sizeBytes,
            VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    gpuBuffer.copyFrom(staging, commandPool);
    staging.dispose();

    log.debug("VulkanVertexBuffer created ({} floats)", vertices.length);
  }

  /** Returns the raw VkBuffer handle for use in vkCmdBindVertexBuffers. */
  long bufferHandle() {
    return gpuBuffer.handle();
  }

  @Override
  public int getId() {
    // Vulkan buffer handles are long — return 0, use bufferHandle() instead
    return 0;
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public VertexBufferLayout getLayout() {
    return layout;
  }

  @Override
  public void bind() {
    // No-op: binding happens in vkCmdBindVertexBuffers during command recording
  }

  @Override
  public void unbind() {
    // No-op
  }

  @Override
  public void dispose() {
    gpuBuffer.dispose();
    log.debug("VulkanVertexBuffer disposed");
  }
}
