package hu.mudlee.core.render.vulkan;

import static hu.mudlee.core.render.vulkan.VulkanCommandPool.FRAMES_IN_FLIGHT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-frame synchronisation primitives.
 *
 * <p>imageAvailableSemaphore: GPU signals when the swap chain image is ready to render into.
 * renderFinishedSemaphore: GPU signals when rendering is complete, safe to present. inFlightFence:
 * CPU waits on this to avoid overwriting resources of an in-flight frame.
 */
class VulkanSyncObjects implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanSyncObjects.class);

    private final VulkanDevice device;
    private final long[] imageAvailableSemaphores = new long[FRAMES_IN_FLIGHT];
    // One per swapchain image so the presentation engine and the next submit never race on the same
    // semaphore.
    private final long[] renderFinishedSemaphores;
    private final long[] inFlightFences = new long[FRAMES_IN_FLIGHT];

    VulkanSyncObjects(VulkanDevice device, int swapChainImageCount) {
        this.device = device;
        this.renderFinishedSemaphores = new long[swapChainImageCount];

        try (MemoryStack stack = stackPush()) {
            var semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            // Fences start signalled so the first vkWaitForFences at frame 0 doesn't block forever
            var fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            var pLong = stack.mallocLong(1);
            for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
                if (vkCreateSemaphore(device.device(), semaphoreInfo, null, pLong) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create imageAvailableSemaphore[" + i + "]");
                }
                imageAvailableSemaphores[i] = pLong.get(0);

                if (vkCreateFence(device.device(), fenceInfo, null, pLong) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create inFlightFence[" + i + "]");
                }
                inFlightFences[i] = pLong.get(0);
            }

            for (int i = 0; i < swapChainImageCount; i++) {
                if (vkCreateSemaphore(device.device(), semaphoreInfo, null, pLong) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create renderFinishedSemaphore[" + i + "]");
                }
                renderFinishedSemaphores[i] = pLong.get(0);
            }
        }
        log.debug(
                "Vulkan sync objects created ({} frames in flight, {} render-finished semaphores)",
                FRAMES_IN_FLIGHT,
                swapChainImageCount);
    }

    long imageAvailableSemaphore(int frame) {
        return imageAvailableSemaphores[frame];
    }

    long renderFinishedSemaphore(int frame) {
        return renderFinishedSemaphores[frame];
    }

    long inFlightFence(int frame) {
        return inFlightFences[frame];
    }

    @Override
    public void dispose() {
        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            vkDestroySemaphore(device.device(), imageAvailableSemaphores[i], null);
            vkDestroyFence(device.device(), inFlightFences[i], null);
        }
        for (long sem : renderFinishedSemaphores) {
            vkDestroySemaphore(device.device(), sem, null);
        }
        log.debug("Vulkan sync objects destroyed");
    }
}
