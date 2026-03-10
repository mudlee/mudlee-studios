package hu.mudlee.core.input;

/**
 * Provides a per-frame snapshot of the primary gamepad state.
 *
 * <p>Uses GLFW's standardized Gamepad API backed by SDL_GameControllerDB, so the same
 * button/axis layout works across Xbox, PS4/PS5, and most common controllers.
 *
 * <p>Usage:
 *
 * <pre>
 * var pad = Gamepad.getState();
 * if (pad.isButtonDown(GamepadButton.A)) { jump(); }
 * float moveX = pad.getAxis(GamepadAxis.LEFT_X);
 * </pre>
 *
 * <p>For action-based input (recommended), bind gamepad buttons and axes directly on an
 * {@link InputAction} via {@link InputAction#addBinding(GamepadButton)} and
 * {@link InputAction#addStickCompositeBinding}.
 */
public final class Gamepad {

    private Gamepad() {}

    /**
     * Returns the current frame's {@link GamepadState} for the primary connected gamepad.
     *
     * <p>Returns a zeroed state if no gamepad is connected.
     */
    public static GamepadState getState() {
        return InputSystem.getGamepadState();
    }

    /** Returns {@code true} if at least one gamepad is currently connected. */
    public static boolean isConnected() {
        return InputSystem.isGamepadConnected();
    }
}
