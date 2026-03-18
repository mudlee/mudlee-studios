package hu.mudlee.core.render;

import hu.mudlee.core.render.types.IndexType;

public abstract class ElementBuffer {
    public static ElementBuffer create(int[] indices) {
        return Renderer.backendFactory().createElementBuffer(indices);
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract IndexType getIndexType();

    public abstract void dispose();
}
