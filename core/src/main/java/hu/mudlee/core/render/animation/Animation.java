package hu.mudlee.core.render.animation;

import hu.mudlee.core.render.texture.TextureRegion;

/**
 * A stateless sequence of {@link TextureRegion} frames.
 *
 * <p>Stateless means one instance can be shared across multiple {@link AnimationPlayer}s
 * simultaneously. Pass a state time (accumulated seconds) to {@link #getKeyFrame} to
 * retrieve the correct frame for that moment.
 */
public final class Animation {

    public final String name;
    public final PlayMode playMode;
    public final float frameDuration;

    private final TextureRegion[] frames;

    public Animation(String name, TextureRegion[] frames, float frameDuration, PlayMode playMode) {
        this.name = name;
        this.frames = frames;
        this.frameDuration = frameDuration;
        this.playMode = playMode;
    }

    public TextureRegion getKeyFrame(float stateTime) {
        int frameCount = frames.length;
        if (frameCount == 1) {
            return frames[0];
        }

        int frameIndex = (int) (stateTime / frameDuration);

        switch (playMode) {
            case ONCE -> frameIndex = Math.min(frameIndex, frameCount - 1);
            case LOOP -> frameIndex = frameIndex % frameCount;
            case LOOP_PINGPONG -> {
                int pos = frameIndex % frameCount;
                int cycle = (frameIndex / frameCount) % 2;
                frameIndex = (cycle == 0) ? pos : (frameCount - 1 - pos);
            }
        }

        return frames[frameIndex];
    }

    public boolean isFinished(float stateTime) {
        return playMode == PlayMode.ONCE && stateTime >= frameDuration * frames.length;
    }

    public int getFrameCount() {
        return frames.length;
    }

    public float getTotalDuration() {
        return frameDuration * frames.length;
    }
}
