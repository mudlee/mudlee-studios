package hu.mudlee.core.render.texture;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.vulkan.VulkanTexture2D;
import java.nio.ByteBuffer;

public abstract class Texture2D implements Disposable {
    public static Texture2D create(String path) {
        return new VulkanTexture2D(path);
    }

    public static Texture2D createFromPixels(ByteBuffer pixels, int width, int height) {
        return createFromPixels(pixels, width, height, false);
    }

    public static Texture2D createFromPixels(ByteBuffer pixels, int width, int height, boolean pixelPerfect) {
        return new VulkanTexture2D(pixels, width, height, pixelPerfect);
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void bind();

    public abstract void dispose();
}
