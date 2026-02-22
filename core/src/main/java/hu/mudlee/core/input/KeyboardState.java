package hu.mudlee.core.input;

/**
 * An immutable snapshot of the keyboard state for the current frame.
 *
 * <p>Obtain via {@link Keyboard#getState()}. Snapshots are lightweight — query them freely within
 * {@code update()}.
 *
 * <pre>
 * var kb = Keyboard.getState();
 * if (kb.isKeyDown(Keys.ESCAPE)) { exit(); }
 * if (kb.isKeyUp(Keys.SPACE))    { ... }
 * </pre>
 */
public final class KeyboardState {

    private final boolean[] pressed;

    KeyboardState(boolean[] pressed) {
        this.pressed = pressed;
    }

    /** Returns {@code true} if the given key is currently held down. */
    public boolean isKeyDown(Keys key) {
        return pressed[key.ordinal()];
    }

    /** Returns {@code true} if the given key is not currently pressed. */
    public boolean isKeyUp(Keys key) {
        return !pressed[key.ordinal()];
    }
}
