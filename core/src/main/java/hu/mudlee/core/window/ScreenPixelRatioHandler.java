package hu.mudlee.core.window;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenPixelRatioHandler {
    // https://en.wikipedia.org/wiki/4K_resolution
    static final int UHD_MIN_WIDTH = 3840;
    static final int UHD_MIN_HEIGHT = 1716;

    private static final Logger log = LoggerFactory.getLogger(ScreenPixelRatioHandler.class);
    private static int ratioTmp;

    public static int get() {
        return ratioTmp;
    }

    public static int set(long windowId, GLFWVidMode vidMode) {
        // Check if the monitor is 4K
        if (vidMode.width() >= UHD_MIN_WIDTH && vidMode.height() >= UHD_MIN_HEIGHT) {
            ratioTmp = 2;
            log.debug("Screen pixel ratio has been calculated to: 2");
            return ratioTmp;
        }

        try (var stack = MemoryStack.stackPush()) {
            var widthScreenCoordBuf = stack.mallocInt(1);
            var heightScreenCoordBuf = stack.mallocInt(1);
            var widthPixelsBuf = stack.mallocInt(1);
            var heightPixelsBuf = stack.mallocInt(1);

            glfwGetWindowSize(windowId, widthScreenCoordBuf, heightScreenCoordBuf);
            glfwGetFramebufferSize(windowId, widthPixelsBuf, heightPixelsBuf);

            ratioTmp = (int) Math.floor((float) widthPixelsBuf.get(0) / (float) widthScreenCoordBuf.get(0));
            log.debug("Screen pixel ratio has been calculated to: {}", ratioTmp);

            return ratioTmp;
        }
    }
}
