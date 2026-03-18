package hu.mudlee.core.render;

public abstract class VertexBuffer {
    public static VertexBuffer create(float[] vertices, VertexBufferLayout layout) {
        return Renderer.backendFactory().createVertexBuffer(vertices, layout);
    }

    public static VertexBuffer createDynamic(VertexBufferLayout layout, int maxFloats) {
        return Renderer.backendFactory().createDynamicVertexBuffer(layout, maxFloats);
    }

    public void update(float[] data, int floatCount) {
        throw new UnsupportedOperationException("This VertexBuffer does not support dynamic float updates");
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract VertexBufferLayout getLayout();

    public abstract void dispose();
}
