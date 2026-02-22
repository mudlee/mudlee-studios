package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.function.Consumer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/**
 * Low-level Vulkan buffer + device memory pair. Used as a building block for vertex, index,
 * uniform, and staging buffers.
 */
class VulkanBuffer implements Disposable {

  private final VulkanDevice device;
  private final long handle;
  private final long memory;
  final long size;

  VulkanBuffer(VulkanDevice device, long size, int usage, int memoryPropertyFlags) {
    this.device = device;
    this.size = size;

    try (MemoryStack stack = stackPush()) {
      VkBufferCreateInfo bufferInfo =
          VkBufferCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
              .size(size)
              .usage(usage)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

      LongBuffer pBuffer = stack.mallocLong(1);
      if (vkCreateBuffer(device.device(), bufferInfo, null, pBuffer) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create Vulkan buffer");
      }
      handle = pBuffer.get(0);

      VkMemoryRequirements memReqs = VkMemoryRequirements.malloc(stack);
      vkGetBufferMemoryRequirements(device.device(), handle, memReqs);

      VkMemoryAllocateInfo allocInfo =
          VkMemoryAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
              .allocationSize(memReqs.size())
              .memoryTypeIndex(
                  VulkanMemoryUtil.findMemoryType(
                      device.memoryProperties(), memReqs.memoryTypeBits(), memoryPropertyFlags));

      LongBuffer pMemory = stack.mallocLong(1);
      if (vkAllocateMemory(device.device(), allocInfo, null, pMemory) != VK_SUCCESS) {
        throw new RuntimeException("Failed to allocate Vulkan buffer memory");
      }
      memory = pMemory.get(0);

      vkBindBufferMemory(device.device(), handle, memory, 0);
    }
  }

  long handle() {
    return handle;
  }

  /**
   * Maps host-visible memory, invokes the consumer with a ByteBuffer view, then unmaps. Only valid
   * for buffers created with VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.
   */
  void map(Consumer<ByteBuffer> action) {
    try (MemoryStack stack = stackPush()) {
      PointerBuffer ppData = stack.mallocPointer(1);
      vkMapMemory(device.device(), memory, 0, size, 0, ppData);
      action.accept(ppData.getByteBuffer(0, (int) size));
      vkUnmapMemory(device.device(), memory);
    }
  }

  /**
   * Copies contents of {@code src} into this buffer via a single-use command buffer. {@code src}
   * must be host-visible (staging), {@code this} can be device-local.
   */
  void copyFrom(VulkanBuffer src, VulkanCommandPool commandPool) {
    try (MemoryStack stack = stackPush()) {
      VkCommandBuffer cmdBuf = commandPool.beginSingleUse(stack);

      VkBufferCopy.Buffer copyRegion =
          VkBufferCopy.calloc(1, stack).srcOffset(0).dstOffset(0).size(src.size);

      vkCmdCopyBuffer(cmdBuf, src.handle, handle, copyRegion);
      commandPool.endSingleUse(cmdBuf);
    }
  }

  @Override
  public void dispose() {
    vkDestroyBuffer(device.device(), handle, null);
    vkFreeMemory(device.device(), memory, null);
  }
}
