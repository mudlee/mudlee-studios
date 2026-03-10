package hu.mudlee.core.input;

/**
 * Standardized gamepad buttons as reported by GLFW's Gamepad API.
 *
 * <p>Declaration order matches {@code GLFW_GAMEPAD_BUTTON_*} integer constants (0–14),
 * so {@code ordinal()} equals the GLFW code.
 *
 * <p>PS4/PS5 aliases: {@link #A} = Cross, {@link #B} = Circle, {@link #X} = Square,
 * {@link #Y} = Triangle.
 */
public enum GamepadButton {
    A,
    B,
    X,
    Y,
    LEFT_BUMPER,
    RIGHT_BUMPER,
    BACK,
    START,
    GUIDE,
    LEFT_THUMB,
    RIGHT_THUMB,
    DPAD_UP,
    DPAD_RIGHT,
    DPAD_DOWN,
    DPAD_LEFT;

    public int glfwCode() {
        return ordinal();
    }
}
