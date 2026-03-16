package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import org.joml.Vector4f;

public interface GraphicsContext extends Disposable {
    void windowPrepared();

    void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync);

    void setClearColor(Vector4f color);

    void clear();

    void renderRaw(VertexArray vao, Shader shader);

    default void renderRaw(VertexArray vao, Shader shader, int elementOffset, int elementCount) {}

    void swapBuffers(float frameTime);

    void windowResized(int newWidth, int newHeight);

    /**
     * Redirects subsequent draw calls to {@code renderTarget}, or to the backbuffer if {@code null}.
     * Must be called while a frame is in progress (after {@link #clear()} and before
     * {@link #swapBuffers(float)}).
     */
    default void setRenderTarget(RenderTarget renderTarget) {}

    /** Block until the GPU has finished all in-flight work. No-op for stateless backends. */
    default void waitIdle() {}

    /** Returns a human-readable string identifying the GPU and backend, e.g. "RTX 4090 (Vulkan)". */
    default String getRendererInfo() {
        return "";
    }
}
