package hu.mudlee.core.render.texture;

import hu.mudlee.core.render.animation.Animation;
import hu.mudlee.core.render.animation.PlayMode;

/**
 * Extracts {@link TextureRegion} frames from a uniformly-gridded sprite sheet.
 *
 * <pre>
 * var sheet = new SpriteSheet(texture, 48, 48)
 *         .withMargin(0, 0)
 *         .withSpacing(0, 0);
 *
 * var idle = sheet.createAnimation("Idle", 0, 0, 6, 0.15f, PlayMode.LOOP);
 * var run  = sheet.createAnimation("Run",  4, 0, 6, 0.1f,  PlayMode.LOOP);
 * </pre>
 */
public final class SpriteSheet {

    private final Texture2D texture;
    private final int frameWidth;
    private final int frameHeight;
    private int marginX = 0;
    private int marginY = 0;
    private int spacingX = 0;
    private int spacingY = 0;

    public SpriteSheet(Texture2D texture, int frameWidth, int frameHeight) {
        this.texture = texture;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public SpriteSheet withMargin(int x, int y) {
        this.marginX = x;
        this.marginY = y;
        return this;
    }

    public SpriteSheet withSpacing(int x, int y) {
        this.spacingX = x;
        this.spacingY = y;
        return this;
    }

    /** Returns the {@link TextureRegion} at the given column and row (both 0-based). */
    public TextureRegion getRegion(int col, int row) {
        int x = marginX + col * (frameWidth + spacingX);
        int y = marginY + row * (frameHeight + spacingY);
        return new TextureRegion(texture, x, y, frameWidth, frameHeight);
    }

    /**
     * Extracts {@code frameCount} consecutive frames from {@code row} starting at
     * {@code startCol} and wraps them in an {@link Animation}.
     */
    public Animation createAnimation(
            String name, int row, int startCol, int frameCount, float frameDuration, PlayMode mode) {
        var frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = getRegion(startCol + i, row);
        }
        return new Animation(name, frames, frameDuration, mode);
    }
}
