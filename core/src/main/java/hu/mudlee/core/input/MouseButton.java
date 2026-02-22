package hu.mudlee.core.input;

import static org.lwjgl.glfw.GLFW.*;

import java.util.HashMap;
import java.util.Map;

/** Mouse button constants. Hides GLFW button indices from game code. */
public enum MouseButton {
    LEFT(GLFW_MOUSE_BUTTON_LEFT),
    RIGHT(GLFW_MOUSE_BUTTON_RIGHT),
    MIDDLE(GLFW_MOUSE_BUTTON_MIDDLE),
    BACK(GLFW_MOUSE_BUTTON_4),
    FORWARD(GLFW_MOUSE_BUTTON_5);

    /** GLFW button index for this button. Package-private — not part of the public API. */
    final int glfwCode;

    private static final Map<Integer, MouseButton> BY_GLFW_CODE = new HashMap<>();

    static {
        for (var button : values()) {
            BY_GLFW_CODE.put(button.glfwCode, button);
        }
    }

    MouseButton(int glfwCode) {
        this.glfwCode = glfwCode;
    }

    /**
     * Returns the {@link MouseButton} for the given GLFW button index, or {@code null} if no
     * constant maps to that index.
     */
    static MouseButton fromGlfwCode(int glfwCode) {
        return BY_GLFW_CODE.get(glfwCode);
    }
}
