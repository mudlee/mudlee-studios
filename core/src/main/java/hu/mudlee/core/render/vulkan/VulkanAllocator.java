package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.VK12.VK_MAKE_API_VERSION;
import static org.lwjgl.vulkan.VK12.VK_SUCCESS;

import hu.mudlee.core.Disposable;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around the VMA (Vulkan Memory Allocator) allocator handle.
 *
 * <p>VMA sub-allocates from large device-memory blocks, reducing the number of actual
 * vkAllocateMemory calls to O(heap types) regardless of how many buffers and images are created.
 * This avoids the driver's maxMemoryAllocationCount limit (commonly 4096 on NVIDIA, 1024 on ARM).
 */
class VulkanAllocator implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanAllocator.class);

    private final long handle;

    VulkanAllocator(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device) {
        try (var stack = stackPush()) {
            var vulkanFunctions = VmaVulkanFunctions.calloc(stack).set(instance, device);

            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .physicalDevice(physicalDevice)
                    .device(device)
                    .instance(instance)
                    .vulkanApiVersion(VK_MAKE_API_VERSION(0, 1, 2, 0))
                    .pVulkanFunctions(vulkanFunctions);

            var pAllocator = stack.mallocPointer(1);
            if (vmaCreateAllocator(createInfo, pAllocator) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VMA allocator");
            }
            handle = pAllocator.get(0);
        }
        log.debug("VMA allocator created");
    }

    long handle() {
        return handle;
    }

    @Override
    public void dispose() {
        vmaDestroyAllocator(handle);
        log.debug("VMA allocator destroyed");
    }
}
