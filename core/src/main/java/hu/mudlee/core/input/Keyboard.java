package hu.mudlee.core.input;

/**
 * Provides a per-frame snapshot of the keyboard state.
 *
 * <p>Usage:
 *
 * <pre>
 * var kb = Keyboard.getState();
 * if (kb.isKeyDown(Keys.ESCAPE)) { exit(); }
 * </pre>
 */
public final class Keyboard {

    private Keyboard() {}

    /**
     * Returns the current frame's {@link KeyboardState}.
     *
     * <p>Call once per frame and cache the result; calling it multiple times in the same frame
     * returns the same logical state.
     */
    public static KeyboardState getState() {
        return InputSystem.getKeyboardState();
    }
}
