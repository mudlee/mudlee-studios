package hu.mudlee.core.window;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.input.InputSystem;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Window implements Disposable {
    private static final Logger log = LoggerFactory.getLogger(Window.class);
    private static Window instance;
    private WindowPreferences preferences;
    private final Vector2i size = new Vector2i();
    private final List<WindowEventListener> listeners = new ArrayList<>();
    private long id;
    private GLFWVidMode glfwVidMode;

    private Window() {
        this.preferences = WindowPreferences.builder().build();
    }

    public static Window get() {
        if (instance == null) {
            instance = new Window();
        }

        return instance;
    }

    @Override
    public void dispose() {
        glfwFreeCallbacks(id);
        glfwDestroyWindow(id);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    public static void remove() {
        get().dispose();
    }

    public static void setPreferences(WindowPreferences preferences) {
        get().preferences = preferences;
    }

    public static int addListener(WindowEventListener listener) {
        get().listeners.add(listener);
        return get().listeners.size() - 1;
    }

    public static Vector2i getSize() {
        return get().size;
    }

    public static void create() {
        log.info("Creating window...");

        if (!glfwInit()) {
            throw new RuntimeException("Could not initialize glfw");
        }

        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        final var window = get();

        window.glfwVidMode = pickMonitor();

        if (window.preferences.getAntialiasing() != Antialiasing.OFF) {
            log.debug("Antialiasing: {}", window.preferences.getAntialiasing().value);
            glfwWindowHint(GLFW_SAMPLES, window.preferences.getAntialiasing().value);
        }

        if (window.preferences.isFullscreen()) {
            window.size.set(window.glfwVidMode.width(), window.glfwVidMode.height());
            glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
        } else {
            window.size.set(window.preferences.getWidth(), window.preferences.getHeight());
        }

        window.listeners.forEach(WindowEventListener::onWindowPrepared);

        window.id = glfwCreateWindow(
                window.size.x,
                window.size.y,
                window.preferences.getTitle(),
                window.preferences.isFullscreen() ? glfwGetPrimaryMonitor() : NULL,
                NULL);

        if (window.id == NULL) {
            glfwTerminate();
            throw new RuntimeException("Error creating GLFW window");
        }

        window.listeners.forEach(listener ->
                listener.onWindowCreated(window.id, window.size.x, window.size.y, window.preferences.isvSync()));

        glfwSetFramebufferSizeCallback(window.id, window::framebufferResized);
        ScreenPixelRatioHandler.set(window.id, window.glfwVidMode);

        log.debug("Setting up input callbacks");
        glfwSetCursorPosCallback(window.id, (w, x, y) -> InputSystem.processMouseMove(x, y));
        glfwSetMouseButtonCallback(window.id, (w, button, action, mods) -> InputSystem.processMouseButton(button, action));
        glfwSetScrollCallback(window.id, (w, xOffset, yOffset) -> InputSystem.processMouseScroll(xOffset, yOffset));
        glfwSetKeyCallback(window.id, (w, key, scancode, action, mods) -> InputSystem.processKey(key, action));

        if (!window.preferences.isFullscreen()) {
            glfwSetWindowPos(
                    window.id,
                    (window.glfwVidMode.width() - window.size.x) / 2,
                    (window.glfwVidMode.height() - window.size.y) / 2);
        }

        log.debug("Window has been setup");
        glfwShowWindow(window.id);
    }

    public static boolean shouldClose() {
        return glfwWindowShouldClose(get().id);
    }

    public static void pollEvents() {
        glfwPollEvents();
    }

    public static void close() {
        glfwSetWindowShouldClose(get().id, true);
    }

    private static GLFWVidMode pickMonitor() {
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

        for (WindowEventListener listener : listeners) {
            listener.onWindowResized(width, height);
        }

        ScreenPixelRatioHandler.set(windowId, glfwVidMode);
        log.trace("Framebuffer size change to {}x{}", width, height);
    }
}
