package hu.mudlee.core.render.texture;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.opengl.OpenGLTexture2D;
import hu.mudlee.core.render.vulkan.VulkanTexture2D;

public abstract class Texture2D implements Disposable {
    public static Texture2D create(String path) {
        return switch (Renderer.activeBackend()) {
            case OPENGL -> new OpenGLTexture2D(path);
            case VULKAN -> new VulkanTexture2D(path);
        };
    }

    public static Texture2D createFromPixels(java.nio.ByteBuffer pixels, int width, int height) {
        return switch (Renderer.activeBackend()) {
            case OPENGL -> new OpenGLTexture2D(pixels, width, height);
            case VULKAN -> new VulkanTexture2D(pixels, width, height);
        };
    }

    public abstract int getWidth();

    public abstract int getHeight();

    /** Returns the backend-native texture handle (e.g. GL texture ID for OpenGL). */
    public abstract int getNativeHandle();

    public abstract void bind();

    public abstract void unBind();

    public abstract void dispose();
}
