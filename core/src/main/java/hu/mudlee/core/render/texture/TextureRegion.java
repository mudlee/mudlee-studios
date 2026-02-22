package hu.mudlee.core.render.texture;

/**
 * An immutable sub-rectangle of a {@link Texture2D} in pixel coordinates.
 *
 * <p>UV coordinates (0.0–1.0) are pre-computed at construction time so the render loop
 * never performs a division per frame.
 */
public final class TextureRegion {

    public final Texture2D texture;
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    public TextureRegion(Texture2D texture, int x, int y, int width, int height) {
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        float tw = texture.getWidth();
        float th = texture.getHeight();
        this.u0 = x / tw;
        this.v0 = y / th;
        this.u1 = (x + width) / tw;
        this.v1 = (y + height) / th;
    }

    public float u0() {
        return u0;
    }

    public float v0() {
        return v0;
    }

    public float u1() {
        return u1;
    }

    public float v1() {
        return v1;
    }
}
