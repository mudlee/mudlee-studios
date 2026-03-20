package hu.mudlee.core.window;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public enum CursorMode {
    NORMAL(GLFW_CURSOR_NORMAL),
    HIDDEN(GLFW_CURSOR_HIDDEN),
    DISABLED(GLFW_CURSOR_DISABLED);

    private final int glfwValue;

    CursorMode(int glfwValue) {
        this.glfwValue = glfwValue;
    }

    int glfwValue() {
        return glfwValue;
    }
}
