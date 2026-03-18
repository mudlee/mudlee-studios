package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.texture.Texture2D;
import org.joml.Vector4f;

public interface GraphicsContext extends Disposable {
    void windowPrepared();

    void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync);

    void setClearColor(Vector4f color);

    boolean beginFrame();

    default void beginRenderPass(RenderTarget renderTarget) {
        beginRenderPass(renderTarget, RenderPassOptions.clearColor());
    }

    default void beginRenderPass(RenderTarget renderTarget, RenderPassOptions options) {}

    default void endRenderPass() {}

    void renderRaw(VertexArray vao, Shader shader);

    default void renderRaw(VertexArray vao, Shader shader, Texture2D texture) {
        renderRaw(vao, shader, texture, 0, -1);
    }

    default void renderRaw(VertexArray vao, Shader shader, Texture2D texture, int elementOffset, int elementCount) {}

    default void renderRaw(VertexArray vao, Shader shader, int elementOffset, int elementCount) {}

    void present(float frameTime);

    void windowResized(int newWidth, int newHeight);

    /** Block until the GPU has finished all in-flight work. No-op for stateless backends. */
    default void waitIdle() {}

    /** Returns a human-readable string identifying the GPU and backend, e.g. "RTX 4090 (Vulkan)". */
    default String getRendererInfo() {
        return "";
    }
}
