package hu.mudlee.core.input;

/**
 * An immutable snapshot of the gamepad state for the current frame.
 *
 * <p>Obtain via {@link Gamepad#getState()}. Returns zeroed state when no gamepad is connected.
 *
 * <pre>
 * var pad = Gamepad.getState();
 * if (pad.isButtonDown(GamepadButton.A)) { jump(); }
 * float x = pad.getAxis(GamepadAxis.LEFT_X);
 * </pre>
 */
public final class GamepadState {

    private final boolean[] buttons;
    private final float[] axes;

    GamepadState(boolean[] buttons, float[] axes) {
        this.buttons = buttons;
        this.axes = axes;
    }

    /** Returns {@code true} if the given button is currently held down. */
    public boolean isButtonDown(GamepadButton button) {
        return buttons[button.glfwCode()];
    }

    /** Returns {@code true} if the given button is not currently pressed. */
    public boolean isButtonUp(GamepadButton button) {
        return !buttons[button.glfwCode()];
    }

    /**
     * Returns the current value of the given axis.
     *
     * <p>Stick axes are in the range −1.0 to +1.0 with deadzone already applied.
     * Trigger axes are in the range 0.0 to +1.0.
     * Y-axis values are <em>not</em> inverted here — use {@link InputAction} bindings
     * for automatic inversion.
     */
    public float getAxis(GamepadAxis axis) {
        return axes[axis.glfwCode()];
    }
}
