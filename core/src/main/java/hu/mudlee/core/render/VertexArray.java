package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLVertexArray;

import java.util.List;
import java.util.Optional;

public abstract class VertexArray {
    public static VertexArray create(){
        return new OpenGLVertexArray();
    }

    public abstract void bind();

    public abstract void unbind();

    public abstract void addVertexBuffer(VertexBuffer buffer);

    public abstract void setIndexBuffer(IndexBuffer indexBuffer);

    public abstract void setInstanceCount(int count);

    public abstract List<VertexBuffer> getVertexBuffers();

    public abstract Optional<IndexBuffer> getIndexBuffer();

    public abstract int getInstanceCount();

    public abstract boolean isInstanced();

    public abstract void dispose();
}
