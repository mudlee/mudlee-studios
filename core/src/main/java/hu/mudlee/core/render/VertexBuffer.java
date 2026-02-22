package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLVertexBuffer;
import hu.mudlee.core.render.vulkan.VulkanVertexBuffer;

public abstract class VertexBuffer {
    public static VertexBuffer create(float[] vertices, VertexBufferLayout layout, int bufferUsage) {
        return switch (Renderer.activeBackend()) {
            case OPENGL -> new OpenGLVertexBuffer(vertices, layout, bufferUsage);
            case VULKAN -> new VulkanVertexBuffer(vertices, layout);
        };
    }

    public static VertexBuffer createDynamic(VertexBufferLayout layout, int maxFloats) {
        return switch (Renderer.activeBackend()) {
            case OPENGL -> new OpenGLVertexBuffer(layout, maxFloats);
            case VULKAN -> new VulkanVertexBuffer(layout, maxFloats);
        };
    }

    public void update(float[] data, int floatCount) {
        throw new UnsupportedOperationException("This VertexBuffer does not support dynamic updates");
    }

    public abstract int getId();

    public abstract int getLength();

    public abstract VertexBufferLayout getLayout();

    public abstract void bind();

    public abstract void unbind();

    public abstract void dispose();
}
