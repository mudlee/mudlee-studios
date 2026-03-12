package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.opengl.OpenGLRenderTarget;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.vulkan.VulkanRenderTarget;

/**
 * An off-screen render surface that can be drawn into and then sampled as a texture.
 *
 * <p>Usage:
 *
 * <pre>
 * var rt = RenderTarget.create(width, height);
 * // in draw():
 * Renderer.setRenderTarget(rt);
 * // ... draw scene to rt ...
 * Renderer.setRenderTarget(null); // restore backbuffer
 * spriteBatch.draw(rt.getColorTexture(), ...);
 * // on shutdown:
 * rt.dispose();
 * </pre>
 */
public abstract class RenderTarget implements Disposable {

    public static RenderTarget create(int width, int height) {
        return switch (Renderer.activeBackend()) {
            case OPENGL -> new OpenGLRenderTarget(width, height);
            case VULKAN -> new VulkanRenderTarget(width, height);
        };
    }

    public abstract int getWidth();

    public abstract int getHeight();

    /** Returns the color attachment as a {@link Texture2D} that can be drawn with a sprite batch. */
    public abstract Texture2D getColorTexture();

    /** Resizes the render target, recreating GPU resources if the dimensions changed. */
    public abstract void resize(int width, int height);
}
