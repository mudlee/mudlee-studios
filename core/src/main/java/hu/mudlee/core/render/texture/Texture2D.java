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

    public abstract void bind();

    public abstract void unBind();

    public abstract void dispose();
}
