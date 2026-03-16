package hu.mudlee.core;

import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.window.Window;
import org.joml.Vector4f;

/**
 * Public GPU facade exposed to game code.
 *
 * <p>Instantiated once by {@link Game} after the renderer is initialised; accessed via
 * {@link Game#graphicsDevice}.
 */
public final class GraphicsDevice {

    private final Vector4f clearColorVec = new Vector4f();

    GraphicsDevice() {}

    /** Clears the back-buffer with the given colour. Call once at the start of {@code draw()}. */
    public void clear(Color color) {
        Renderer.setClearColor(clearColorVec.set(color.r, color.g, color.b, color.a));
        Renderer.clear();
    }

    /** Returns a {@link Viewport} that covers the full window. */
    public Viewport getViewport() {
        var size = Window.getSize();
        return new Viewport(0, 0, size.x, size.y);
    }

    /** Returns the active rendering backend. */
    public RenderBackend getBackend() {
        return Renderer.activeBackend();
    }
}
