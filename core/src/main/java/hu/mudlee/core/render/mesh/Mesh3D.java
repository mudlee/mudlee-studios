package hu.mudlee.core.render.mesh;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;

/** Minimal indexed 3D mesh container. */
public final class Mesh3D implements Disposable {

    private final VertexArray vertexArray;
    private final VertexBuffer vertexBuffer;
    private final ElementBuffer indexBuffer;
    private final int indexCount;

    public Mesh3D(float[] vertices, int[] indices, VertexBufferLayout layout) {
        vertexBuffer = VertexBuffer.create(vertices, layout);
        indexBuffer = ElementBuffer.create(indices);
        vertexArray = VertexArray.create();
        vertexArray.addVBO(vertexBuffer);
        vertexArray.setEBO(indexBuffer);
        indexCount = indices.length;
    }

    public void draw(Shader shader) {
        Renderer.renderRaw(vertexArray, shader, 0, indexCount);
    }

    @Override
    public void dispose() {
        indexBuffer.dispose();
        vertexBuffer.dispose();
        vertexArray.dispose();
    }
}
