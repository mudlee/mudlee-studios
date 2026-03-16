package hu.mudlee.core.render;

import hu.mudlee.core.render.types.IndexType;
import hu.mudlee.core.render.vulkan.VulkanIndexBuffer;

public abstract class ElementBuffer {
    public static ElementBuffer create(int[] indices) {
        return new VulkanIndexBuffer(indices);
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract IndexType getIndexType();

    public abstract void dispose();
}
