package hu.mudlee.core.input;

import org.joml.Vector2f;

/**
 * An immutable snapshot of the mouse state for the current frame.
 *
 * <p>Obtain via {@link Mouse#getState()}. All positions are in window pixel coordinates with the
 * origin at the top-left corner.
 *
 * <pre>
 * var ms = Mouse.getState();
 * if (ms.isButtonDown(MouseButton.LEFT)) { ... }
 * var pos = ms.position();
 * </pre>
 */
public final class MouseState {

    private final float x;
    private final float y;
    private final float deltaX;
    private final float deltaY;
    private final float scrollX;
    private final float scrollY;
    private final boolean[] buttons;

    MouseState(float x, float y, float deltaX, float deltaY, float scrollX, float scrollY, boolean[] buttons) {
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.buttons = buttons;
    }

    /** Horizontal cursor position in window pixel coordinates. */
    public float x() {
        return x;
    }

    /** Vertical cursor position in window pixel coordinates. */
    public float y() {
        return y;
    }

    /** Cursor position as a {@link Vector2f}. Allocates a new vector each call. */
    public Vector2f position() {
        return new Vector2f(x, y);
    }

    /** Writes cursor position into {@code dest} and returns it. Preferred in hot paths. */
    public Vector2f position(Vector2f dest) {
        return dest.set(x, y);
    }

    /** Horizontal mouse movement accumulated during the current frame. */
    public float deltaX() {
        return deltaX;
    }

    /** Vertical mouse movement accumulated during the current frame. */
    public float deltaY() {
        return deltaY;
    }

    /** Horizontal scroll offset accumulated since the last frame. */
    public float scrollX() {
        return scrollX;
    }

    /** Vertical scroll offset accumulated since the last frame. */
    public float scrollY() {
        return scrollY;
    }

    /** Returns {@code true} if the given mouse button is currently held down. */
    public boolean isButtonDown(MouseButton button) {
        return buttons[button.ordinal()];
    }

    /** Returns {@code true} if the given mouse button is not currently pressed. */
    public boolean isButtonUp(MouseButton button) {
        return !buttons[button.ordinal()];
    }
}
