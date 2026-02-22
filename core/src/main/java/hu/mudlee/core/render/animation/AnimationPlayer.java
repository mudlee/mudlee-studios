package hu.mudlee.core.render.animation;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.texture.TextureRegion;

/**
 * Stateful driver for an {@link Animation}.
 *
 * <p>Call {@link #play} every frame unconditionally — switching to the same animation that
 * is already playing is a no-op, so the pattern below is safe:
 *
 * <pre>
 * player.play(moving ? run : idle);
 * player.update(gameTime);
 * spriteBatch.draw(player.getCurrentFrame(), position, Color.WHITE);
 * </pre>
 */
public final class AnimationPlayer {

    private Animation current;
    private float stateTime = 0f;

    /**
     * Switches to {@code animation}. If {@code animation} is already playing, does nothing
     * (state time is preserved). Passing {@code null} stops playback.
     */
    public void play(Animation animation) {
        if (current == animation) {
            return;
        }
        current = animation;
        stateTime = 0f;
    }

    /** Advances the state time. Call once per frame before {@link #getCurrentFrame()}. */
    public void update(GameTime gameTime) {
        if (current != null) {
            stateTime += gameTime.elapsedSeconds();
        }
    }

    /**
     * Returns the frame that should be rendered this frame, or {@code null} if no animation
     * has been set.
     */
    public TextureRegion getCurrentFrame() {
        if (current == null) {
            return null;
        }
        return current.getKeyFrame(stateTime);
    }

    /** Returns {@code true} when a {@link PlayMode#ONCE} animation has finished. */
    public boolean isFinished() {
        return current != null && current.isFinished(stateTime);
    }

    /** Resets the state time to 0 without changing the current animation. */
    public void reset() {
        stateTime = 0f;
    }
}
