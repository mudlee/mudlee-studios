package hu.mudlee.core.render.vulkan;

import static hu.mudlee.core.render.vulkan.VulkanCommandPool.FRAMES_IN_FLIGHT;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan vertex buffer.
 *
 * <p><b>Static mode</b> (float[] constructor): device-local GPU memory uploaded once via a staging
 * buffer. Read-only after creation.
 *
 * <p><b>Dynamic mode</b> (layout + maxFloats constructor): {@code FRAMES_IN_FLIGHT} host-visible,
 * host-coherent buffers — one per frame slot. {@link #update} writes into the slot that matches the
 * current in-flight frame index, so the GPU never reads a buffer while the CPU is writing it.
 */
public class VulkanVertexBuffer extends VertexBuffer {

    private static final Logger log = LoggerFactory.getLogger(VulkanVertexBuffer.class);

    private final VulkanBuffer gpuBuffer;
    private final VulkanBuffer[] perFrameBuffers;
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
        this.perFrameBuffers = null;

        var sizeBytes = (long) vertices.length * Float.BYTES;

        var staging = new VulkanBuffer(
                device,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        staging.map(dst -> {
            var floatView = dst.asFloatBuffer();
            floatView.put(vertices).flip();
        });

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
     * Dynamic constructor: allocates {@code FRAMES_IN_FLIGHT} host-visible buffers so each
     * in-flight frame reads from its own slot, eliminating the CPU/GPU write race on dynamic data
     * (e.g. SpriteBatch).
     */
    public VulkanVertexBuffer(VertexBufferLayout layout, int maxFloats) {
        this.layout = layout;
        this.length = 0;
        this.dynamic = true;
        this.gpuBuffer = null;

        var sizeBytes = (long) maxFloats * Float.BYTES;
        var device = VulkanContext.get().device();
        perFrameBuffers = new VulkanBuffer[FRAMES_IN_FLIGHT];
        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            perFrameBuffers[i] = new VulkanBuffer(
                    device,
                    sizeBytes,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        log.debug(
                "VulkanVertexBuffer (dynamic) created (capacity {} floats, {} frame slots)",
                maxFloats,
                FRAMES_IN_FLIGHT);
    }

    @Override
    public void update(float[] data, int floatCount) {
        if (!dynamic) {
            throw new UnsupportedOperationException("Cannot update a static VulkanVertexBuffer");
        }
        this.length = floatCount;
        perFrameBuffers[VulkanContext.get().currentFrame()].map(
                dst -> dst.asFloatBuffer().put(data, 0, floatCount));
    }

    /** Returns the raw VkBuffer handle for use in vkCmdBindVertexBuffers. */
    long bufferHandle() {
        return dynamic ? perFrameBuffers[VulkanContext.get().currentFrame()].handle() : gpuBuffer.handle();
    }

    @Override
    public int getId() {
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
    public void bind() {}

    @Override
    public void unbind() {}

    @Override
    public void dispose() {
        if (dynamic) {
            for (var buf : perFrameBuffers) {
                buf.dispose();
            }
        } else {
            gpuBuffer.dispose();
        }
        log.debug("VulkanVertexBuffer disposed");
    }
}
