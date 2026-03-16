package hu.mudlee.core.render;

import hu.mudlee.core.render.vulkan.VulkanVertexBuffer;
import java.nio.ByteBuffer;

public abstract class VertexBuffer {
    public static VertexBuffer create(float[] vertices, VertexBufferLayout layout) {
        return new VulkanVertexBuffer(vertices, layout);
    }

    public static VertexBuffer createDynamic(VertexBufferLayout layout, int maxFloats) {
        return new VulkanVertexBuffer(layout, maxFloats);
    }

    public void update(float[] data, int floatCount) {
        throw new UnsupportedOperationException("This VertexBuffer does not support dynamic float updates");
    }

    public void update(ByteBuffer data, int byteCount) {
        throw new UnsupportedOperationException("This VertexBuffer does not support dynamic byte updates");
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract VertexBufferLayout getLayout();

    public abstract void bind();

    public abstract void unbind();

    public abstract void dispose();
}
