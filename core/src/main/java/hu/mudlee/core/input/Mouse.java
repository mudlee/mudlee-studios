package hu.mudlee.core.input;

/**
 * Provides a per-frame snapshot of the mouse state.
 *
 * <p>Usage:
 *
 * <pre>
 * var ms = Mouse.getState();
 * if (ms.isButtonDown(MouseButton.LEFT)) { ... }
 * float x = ms.x();
 * </pre>
 */
public final class Mouse {

    private Mouse() {}

    /**
     * Returns the current frame's {@link MouseState}.
     *
     * <p>Call once per frame and cache the result; calling it multiple times in the same frame
     * returns the same logical state.
     */
    public static MouseState getState() {
        return InputSystem.getMouseState();
    }
}
