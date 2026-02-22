package hu.mudlee.core;

/**
 * Defines the drawable region on screen in window pixel coordinates.
 * Obtain the current viewport via {@link GraphicsDevice#getViewport()}.
 */
public final class Viewport {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Viewport(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getAspectRatio() {
        return (float) width / height;
    }
}
