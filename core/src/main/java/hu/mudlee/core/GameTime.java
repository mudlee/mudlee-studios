package hu.mudlee.core;

/**
 * Timing information for the current frame, passed to {@link hu.mudlee.core.scene.Scene#update}.
 *
 * <p>Analogous to MonoGame's {@code GameTime}.
 */
public final class GameTime {

    /** Seconds elapsed since the previous frame. */
    private final float elapsedSeconds;

    /** Total seconds elapsed since the game loop started. */
    private final float totalSeconds;

    /**
     * {@code true} when the previous frame took longer than the target frame time (1/60 s),
     * indicating the game is struggling to maintain the desired update rate.
     */
    private final boolean runningSlowly;

    public GameTime(float elapsedSeconds, float totalSeconds, boolean runningSlowly) {
        this.elapsedSeconds = elapsedSeconds;
        this.totalSeconds = totalSeconds;
        this.runningSlowly = runningSlowly;
    }

    public float elapsedSeconds() {
        return elapsedSeconds;
    }

    public float totalSeconds() {
        return totalSeconds;
    }

    public boolean isRunningSlowly() {
        return runningSlowly;
    }
}
