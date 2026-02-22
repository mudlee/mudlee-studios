package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
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
    private final boolean dynamic;
    private int length;

    /** Convenience constructor — resolves device and command pool from the active VulkanContext. */
    public VulkanVertexBuffer(float[] vertices, VertexBufferLayout layout) {
        this(vertices, layout, VulkanContext.get().device(), VulkanContext.get().commandPool());
    }

    public VulkanVertexBuffer(
            float[] vertices, VertexBufferLayout layout, VulkanDevice device, VulkanCommandPool commandPool) {
        this.layout = layout;
        this.length = vertices.length;
        this.dynamic = false;

        var sizeBytes = (long) vertices.length * Float.BYTES;

        // Stage: host-visible buffer for CPU upload
        var staging = new VulkanBuffer(
                device,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        staging.map(dst -> {
            var floatView = dst.asFloatBuffer();
            floatView.put(vertices).flip();
        });

        // Device-local: fast GPU memory, only accessible from the GPU
        gpuBuffer = new VulkanBuffer(
                device,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        gpuBuffer.copyFrom(staging, commandPool);
        staging.dispose();

        log.debug("VulkanVertexBuffer created ({} floats)", vertices.length);
    }

    /**
     * Dynamic constructor: host-visible memory updated every frame (e.g. SpriteBatch).
     * No staging buffer — CPU writes directly to GPU-visible memory.
     */
    public VulkanVertexBuffer(VertexBufferLayout layout, int maxFloats) {
        this.layout = layout;
        this.length = 0;
        this.dynamic = true;

        gpuBuffer = new VulkanBuffer(
                VulkanContext.get().device(),
                (long) maxFloats * Float.BYTES,
                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        log.debug("VulkanVertexBuffer (dynamic) created (capacity {} floats)", maxFloats);
    }

    @Override
    public void update(float[] data, int floatCount) {
        if (!dynamic) {
            throw new UnsupportedOperationException("Cannot update a static VulkanVertexBuffer");
        }
        this.length = floatCount;
        gpuBuffer.map(dst -> dst.asFloatBuffer().put(data, 0, floatCount));
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
