package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import org.joml.Vector4f;

public interface GraphicsContext extends Disposable {
    void windowPrepared();

    void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync);

    void setClearFlags(int mask);

    void setClearColor(Vector4f color);

    void clear();

    void renderRaw(VertexArray vao, Shader shader, RenderMode renderMode, PolygonMode polygonMode);

    void swapBuffers(float frameTime);

    void windowResized(int newWidth, int newHeight);

    /** Block until the GPU has finished all in-flight work. No-op for stateless backends. */
    default void waitIdle() {}
}
