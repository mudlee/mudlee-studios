package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.types.BlendFactor;
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

    default void renderRaw(
            VertexArray vao,
            Shader shader,
            RenderMode renderMode,
            PolygonMode polygonMode,
            int elementOffset,
            int elementCount) {}

    void swapBuffers(float frameTime);

    void windowResized(int newWidth, int newHeight);

    default void setViewport(int x, int y, int width, int height) {}

    default void setBlend(boolean enable, BlendFactor src, BlendFactor dst) {}

    default void setScissor(boolean enable, int x, int y, int width, int height) {}

    /** Block until the GPU has finished all in-flight work. No-op for stateless backends. */
    default void waitIdle() {}

    /** Returns a human-readable string identifying the GPU and backend, e.g. "RTX 4090 (OpenGL)". */
    default String getRendererInfo() {
        return "";
    }
}
