package hu.mudlee.core.input;

/**
 * Standardized gamepad axes as reported by GLFW's Gamepad API.
 *
 * <p>Declaration order matches {@code GLFW_GAMEPAD_AXIS_*} integer constants (0–5),
 * so {@code ordinal()} equals the GLFW code.
 *
 * <p>Axis ranges: sticks are −1.0 to +1.0; triggers are 0.0 (released) to +1.0 (fully pressed).
 *
 * <p><strong>Note on Y-axis convention:</strong> GLFW reports stick Y as −1.0 = up, +1.0 = down.
 * When using {@link InputAction#addStickCompositeBinding}, the engine automatically inverts Y so
 * that +1.0 = up, consistent with the rest of the engine.
 */
public enum GamepadAxis {
    LEFT_X,
    LEFT_Y,
    RIGHT_X,
    RIGHT_Y,
    LEFT_TRIGGER,
    RIGHT_TRIGGER;

    public int glfwCode() {
        return ordinal();
    }
}
