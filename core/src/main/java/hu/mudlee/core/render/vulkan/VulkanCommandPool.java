package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VulkanCommandPool implements Disposable {

    static final int FRAMES_IN_FLIGHT = 2;

    private static final Logger log = LoggerFactory.getLogger(VulkanCommandPool.class);

    private final VulkanDevice device;
    private final long handle;
    private final VkCommandBuffer[] commandBuffers = new VkCommandBuffer[FRAMES_IN_FLIGHT];

    VulkanCommandPool(VulkanDevice device) {
        this.device = device;

        try (MemoryStack stack = stackPush()) {
            // RESET_COMMAND_BUFFER_BIT allows individual command buffers to be reset
            var poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(device.queueFamilyIndices().graphicsFamily())
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            var pPool = stack.mallocLong(1);
            if (vkCreateCommandPool(device.device(), poolInfo, null, pPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkCommandPool");
            }
            handle = pPool.get(0);

            allocateCommandBuffers(stack);
            log.debug("VkCommandPool created with {} command buffers", FRAMES_IN_FLIGHT);
        }
    }

    private void allocateCommandBuffers(MemoryStack stack) {
        var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(handle)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(FRAMES_IN_FLIGHT);

        var pBuffers = stack.mallocPointer(FRAMES_IN_FLIGHT);
        if (vkAllocateCommandBuffers(device.device(), allocInfo, pBuffers) != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate VkCommandBuffers");
        }

        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            commandBuffers[i] = new VkCommandBuffer(pBuffers.get(i), device.device());
        }
    }

    VkCommandBuffer commandBuffer(int frameIndex) {
        return commandBuffers[frameIndex];
    }

    long poolHandle() {
        return handle;
    }

    /**
     * Begins a one-time-submit command buffer for short transfer/transition operations. Must be
     * paired with {@link #endSingleUse(VkCommandBuffer)}.
     */
    VkCommandBuffer beginSingleUse(MemoryStack stack) {
        var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(handle)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);

        var pBuffer = stack.mallocPointer(1);
        vkAllocateCommandBuffers(device.device(), allocInfo, pBuffer);
        var cmdBuf = new VkCommandBuffer(pBuffer.get(0), device.device());

        var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        vkBeginCommandBuffer(cmdBuf, beginInfo);
        return cmdBuf;
    }

    /** Ends, submits, waits, and frees a single-use command buffer. */
    void endSingleUse(VkCommandBuffer cmdBuf) {
        vkEndCommandBuffer(cmdBuf);

        try (MemoryStack stack = stackPush()) {
            var pCmdBuf = stack.pointers(cmdBuf);
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(pCmdBuf);

            vkQueueSubmit(device.graphicsQueue(), submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(device.graphicsQueue());
            vkFreeCommandBuffers(device.device(), handle, pCmdBuf);
        }
    }

    @Override
    public void dispose() {
        vkDestroyCommandPool(device.device(), handle, null);
        log.debug("VkCommandPool destroyed");
    }
}
