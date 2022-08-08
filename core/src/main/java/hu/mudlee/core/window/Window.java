package hu.mudlee.core.window;

import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private static final Logger log = LoggerFactory.getLogger(Window.class);
    private final WindowPreferences preferences;
    private final Vector2i size = new Vector2i();
    private long id;

    private GLFWVidMode glfwVidMode;

    public Window(WindowPreferences preferences) {
        this.preferences = preferences;
    }

    public void create() {
        log.info("Creating window...");

        if(!glfwInit()) {
            throw new RuntimeException("Could not initialize glfw");
        }

        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        glfwVidMode = pickMonitor();

        if (preferences.getAntialiasing() != Antialiasing.OFF) {
            log.debug("Antialiasing: {}", preferences.getAntialiasing().value);
            glfwWindowHint(GLFW_SAMPLES, preferences.getAntialiasing().value);
        }

        if (preferences.getFullscreen()) {
            size.set(glfwVidMode.width(), glfwVidMode.height());
            glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
        }
        else {
            size.set(preferences.getWidth(), preferences.getHeight());
        }

        // listeners.forEach(WindowEventListener::onWindowPrepared);

        id = glfwCreateWindow(size.x, size.y, preferences.getTitle(), preferences.getFullscreen() ? glfwGetPrimaryMonitor() : NULL, NULL);

        if (id == NULL) {
            throw new RuntimeException("Error creating GLFW window");
        }

        //listeners.forEach(listener -> listener.onWindowCreated(id, size.x, size.y, preferences.vSync));

        glfwSetFramebufferSizeCallback(id, this::framebufferResized);
        ScreenPixelRatioHandler.set(id, glfwVidMode);

        log.debug("Creating input");
        //input.initialize(new Input.InitializationParams(size.x, windowSize.y, this::cursorPositionHasChanged));

        log.debug("Setting up input callbacks");
        //glfwSetKeyCallback(id, (window, key, scancode, action, mods) -> Spck.input.keyCallback(key, scancode, action, mods));
        /*glfwSetCursorPosCallback(id, (window, x, y) -> input.cursorPosCallback(x, y));
        glfwSetScrollCallback(id, (window, xOffset, yOffset) -> input.mouseScrollCallback(xOffset, yOffset));
        glfwSetMouseButtonCallback(id, (window, button, action, mods) -> input.mouseButtonCallback(button, action, mods));
         */

        if (!preferences.getFullscreen()) {
            glfwSetWindowPos(id, (glfwVidMode.width() - size.x) / 2, (glfwVidMode.height() - size.y) / 2);
        }

        log.debug("Window has been setup");
        glfwShowWindow(id);
    }

    private GLFWVidMode pickMonitor() {
        final var buffer = glfwGetMonitors();

        if (buffer == null) {
            throw new RuntimeException("No monitors were found");
        }

        if (buffer.capacity() == 1) {
            log.info("Found one monitor: {}", glfwGetMonitorName(buffer.get()));
        } else {
            log.info("Found multiple monitors:");
            for (int i = 0; i < buffer.capacity(); i++) {
                log.info(" Monitor-{} '{}'", i, glfwGetMonitorName(buffer.get(i)));
            }
        }

        return glfwGetVideoMode(glfwGetPrimaryMonitor());
    }

    private void framebufferResized(long windowId, int width, int height) {
        size.set(width, height);
        //listeners.forEach(listener -> listener.onWindowResized(width, height));
        ScreenPixelRatioHandler.set(windowId, glfwVidMode);
        log.trace("Framebuffer size change to {}x{}", width, height);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(id);
    }

    public void pollEvents() {
        glfwPollEvents();
    }
}
