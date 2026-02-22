package hu.mudlee.core;

import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.types.BufferBitTypes;
import hu.mudlee.core.window.Window;

/**
 * Public GPU facade exposed to game code.
 *
 * <p>Instantiated once by {@link Game} after the renderer is initialised; accessed via
 * {@link Game#graphicsDevice}.
 */
public final class GraphicsDevice {

    GraphicsDevice() {}

    /** Clears the back-buffer with the given colour. Call once at the start of {@code draw()}. */
    public void clear(Color color) {
        Renderer.setClearColor(color.toVector4f());
        Renderer.setClearFlags(BufferBitTypes.COLOR);
        Renderer.clear();
    }

    /** Returns a {@link Viewport} that covers the full window. */
    public Viewport getViewport() {
        var size = Window.getSize();
        return new Viewport(0, 0, size.x, size.y);
    }

    /** Sets the active viewport. */
    public void setViewport(Viewport viewport) {
        Renderer.setViewport(viewport.x, viewport.y, viewport.width, viewport.height);
    }

    /** Returns the active rendering backend. */
    public RenderBackend getBackend() {
        return Renderer.activeBackend();
    }
}
