package hu.mudlee.core.render;

import java.util.List;
import java.util.Optional;

public abstract class VertexArray {
    public static VertexArray create() {
        return Renderer.backendFactory().createVertexArray();
    }

    public abstract void addVBO(VertexBuffer buffer);

    public abstract void setEBO(ElementBuffer elementBuffer);

    public abstract void setInstanceCount(int count);

    public abstract List<VertexBuffer> getVBOs();

    public abstract Optional<ElementBuffer> getEBO();

    public abstract int getInstanceCount();

    public abstract boolean isInstanced();

    public abstract void dispose();
}
