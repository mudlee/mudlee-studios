package hu.mudlee.core.render.vulkan;

import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.types.IndexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan index buffer backed by device-local GPU memory. Index type is always VK_INDEX_TYPE_UINT32
 * (matches int[] input).
 *
 * <p>Index buffer binding is done explicitly via vkCmdBindIndexBuffer inside
 * VulkanContext.renderRaw().
 */
public class VulkanIndexBuffer extends ElementBuffer {

    private static final Logger log = LoggerFactory.getLogger(VulkanIndexBuffer.class);

    private final VulkanBuffer gpuBuffer;
    private final int length;

    /** Convenience constructor — resolves command pool from the active VulkanContext. */
    public VulkanIndexBuffer(int[] indices) {
        this(indices, VulkanContext.get().commandPool());
    }

    public VulkanIndexBuffer(int[] indices, VulkanCommandPool commandPool) {
        this.length = indices.length;
        var sizeBytes = (long) indices.length * Integer.BYTES;

        var staging = new VulkanBuffer(
                sizeBytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VulkanBuffer.AllocationRequest.stagingUpload());

        staging.map(dst -> {
            var intView = dst.asIntBuffer();
            intView.put(indices).flip();
        });

        gpuBuffer = new VulkanBuffer(
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VulkanBuffer.AllocationRequest.deviceLocal());

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
    public IndexType getIndexType() {
        return IndexType.INT;
    }

    @Override
    public void dispose() {
        gpuBuffer.dispose();
        log.debug("VulkanIndexBuffer disposed");
    }
}
