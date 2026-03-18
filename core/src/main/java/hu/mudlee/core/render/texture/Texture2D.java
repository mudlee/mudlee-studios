package hu.mudlee.core.render.texture;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.Renderer;
import java.nio.ByteBuffer;

public abstract class Texture2D implements Disposable {
    public static Texture2D create(String path) {
        return Renderer.backendFactory().createTexture(path);
    }

    public static Texture2D createFromPixels(ByteBuffer pixels, int width, int height) {
        return createFromPixels(pixels, width, height, false);
    }

    public static Texture2D createFromPixels(ByteBuffer pixels, int width, int height, boolean pixelPerfect) {
        return Renderer.backendFactory().createTextureFromPixels(pixels, width, height, pixelPerfect);
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void dispose();
}
