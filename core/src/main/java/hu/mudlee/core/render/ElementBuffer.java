package hu.mudlee.core.render;

import hu.mudlee.core.render.types.IndexType;
import hu.mudlee.core.render.vulkan.VulkanIndexBuffer;
import java.nio.ByteBuffer;

public abstract class ElementBuffer {
    public static ElementBuffer create(int[] indices) {
        return new VulkanIndexBuffer(indices);
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract IndexType getIndexType();

    public void update(ByteBuffer data, int byteCount) {
        throw new UnsupportedOperationException("This ElementBuffer does not support dynamic updates");
    }

    public abstract void bind();

    public abstract void unbind();

    public abstract void dispose();
}
