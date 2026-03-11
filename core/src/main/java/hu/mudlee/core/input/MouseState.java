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
    private final float scrollX;
    private final float scrollY;
    private final boolean[] buttons;

    MouseState(float x, float y, float scrollX, float scrollY, boolean[] buttons) {
        this.x = x;
        this.y = y;
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

    /** Cursor position as a {@link Vector2f}. */
    public Vector2f position() {
        return new Vector2f(x, y);
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
