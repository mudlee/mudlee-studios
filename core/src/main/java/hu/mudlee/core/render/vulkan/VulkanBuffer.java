package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;

/**
 * Low-level Vulkan buffer backed by a VMA sub-allocation. Used as a building block for vertex,
 * index, uniform, and staging buffers.
 *
 * <p>VMA sub-allocates from large device-memory blocks, keeping the total vkAllocateMemory call
 * count at O(heap types) regardless of how many buffers are created.
 */
class VulkanBuffer implements Disposable {

    private final long allocator;
    private final long handle;
    private final long allocation;
    final long size;

    VulkanBuffer(long size, int usage, int memoryPropertyFlags) {
        this.size = size;
        this.allocator = VulkanContext.get().allocator().handle();

        try (MemoryStack stack = stackPush()) {
            var bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var allocationCreateInfo = VmaAllocationCreateInfo.calloc(stack);
            var hostVisible = (memoryPropertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0;
            if (hostVisible) {
                allocationCreateInfo
                        .usage(VMA_MEMORY_USAGE_AUTO)
                        .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            } else {
                allocationCreateInfo.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            }

            var pBuffer = stack.mallocLong(1);
            var pAllocation = stack.mallocPointer(1);
            if (vmaCreateBuffer(allocator, bufferInfo, allocationCreateInfo, pBuffer, pAllocation, null)
                    != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan buffer via VMA");
            }
            handle = pBuffer.get(0);
            allocation = pAllocation.get(0);
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
            var ppData = stack.mallocPointer(1);
            vmaMapMemory(allocator, allocation, ppData);
            action.accept(ppData.getByteBuffer(0, (int) size));
            vmaUnmapMemory(allocator, allocation);
        }
    }

    /**
     * Copies contents of {@code src} into this buffer via a single-use command buffer. {@code src}
     * must be host-visible (staging), {@code this} can be device-local.
     */
    void copyFrom(VulkanBuffer src, VulkanCommandPool commandPool) {
        try (MemoryStack stack = stackPush()) {
            var cmdBuf = commandPool.beginSingleUse(stack);
            var copyRegion =
                    VkBufferCopy.calloc(1, stack).srcOffset(0).dstOffset(0).size(src.size);
            vkCmdCopyBuffer(cmdBuf, src.handle, handle, copyRegion);
            commandPool.endSingleUse(cmdBuf);
        }
    }

    @Override
    public void dispose() {
        vmaDestroyBuffer(allocator, handle, allocation);
    }
}
