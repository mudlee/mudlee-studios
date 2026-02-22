package hu.mudlee.core.render.vulkan;

import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan equivalent of a VAO: a container grouping vertex buffers and an optional index buffer.
 * There is no GPU state object in Vulkan — vertex/index buffers are bound explicitly in the command
 * buffer via vkCmdBindVertexBuffers / vkCmdBindIndexBuffer inside VulkanContext.renderRaw().
 *
 * <p>bind()/unbind() are no-ops.
 */
public class VulkanVertexArray extends VertexArray {

  private static final Logger log = LoggerFactory.getLogger(VulkanVertexArray.class);

  private final List<VertexBuffer> vertexBuffers = new ArrayList<>();
  private ElementBuffer indexBuffer;
  private int instanceCount;
  private boolean instanced;

  @Override
  public void bind() {
    // No-op: no VAO concept in Vulkan
  }

  @Override
  public void unbind() {
    // No-op
  }

  @Override
  public void addVBO(VertexBuffer buffer) {
    if (buffer instanceof VulkanVertexBuffer vvb) {
      vertexBuffers.add(vvb);

      // Check for instanced attributes
      for (var attr : buffer.getLayout().attributes()) {
        if (attr instanceof hu.mudlee.core.render.VertexLayoutInstancedAttribute) {
          instanced = true;
          break;
        }
      }
    } else {
      throw new IllegalArgumentException(
          "VulkanVertexArray only accepts VulkanVertexBuffer instances");
    }
    log.debug("VulkanVertexBuffer added to VulkanVertexArray");
  }

  @Override
  public void setEBO(ElementBuffer elementBuffer) {
    if (elementBuffer instanceof VulkanIndexBuffer) {
      this.indexBuffer = elementBuffer;
    } else {
      throw new IllegalArgumentException(
          "VulkanVertexArray only accepts VulkanIndexBuffer instances");
    }
    log.debug("VulkanIndexBuffer set on VulkanVertexArray");
  }

  @Override
  public void setInstanceCount(int count) {
    this.instanceCount = count;
  }

  @Override
  public List<VertexBuffer> getVBOs() {
    return vertexBuffers;
  }

  @Override
  public Optional<ElementBuffer> getEBO() {
    return Optional.ofNullable(indexBuffer);
  }

  @Override
  public int getInstanceCount() {
    return instanceCount;
  }

  @Override
  public boolean isInstanced() {
    return instanced;
  }

  @Override
  public void dispose() {
    for (VertexBuffer vb : vertexBuffers) {
      vb.dispose();
    }
    if (indexBuffer != null) {
      indexBuffer.dispose();
    }
    log.debug("VulkanVertexArray disposed");
  }
}
