package hu.mudlee.core.window;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenPixelRatioHandler {
  // https://en.wikipedia.org/wiki/4K_resolution
  static final int UHD_MIN_WIDTH = 3840;
  static final int UHD_MIN_HEIGHT = 1716;

  private static final Logger log = LoggerFactory.getLogger(ScreenPixelRatioHandler.class);
  private static int ratioTmp;

  public static int set(long windowId, GLFWVidMode vidMode) {
    // Check if the monitor is 4K
    if (vidMode.width() >= UHD_MIN_WIDTH && vidMode.height() >= UHD_MIN_HEIGHT) {
      log.debug("Screen pixel ratio has been calculated to: 2");
      return 2;
    }

    final var widthScreenCoordBuf = MemoryUtil.memAllocInt(1);
    final var heightScreenCoordBuf = MemoryUtil.memAllocInt(1);
    final var widthPixelsBuf = MemoryUtil.memAllocInt(1);
    final var heightPixelsBuf = MemoryUtil.memAllocInt(1);

    glfwGetWindowSize(windowId, widthScreenCoordBuf, heightScreenCoordBuf);
    glfwGetFramebufferSize(windowId, widthPixelsBuf, heightPixelsBuf);

    ratioTmp = (int) Math.floor((float) widthPixelsBuf.get() / (float) widthScreenCoordBuf.get());
    log.debug("Screen pixel ratio has been calculated to: {}", ratioTmp);

    MemoryUtil.memFree(widthScreenCoordBuf);
    MemoryUtil.memFree(heightScreenCoordBuf);
    MemoryUtil.memFree(widthPixelsBuf);
    MemoryUtil.memFree(heightPixelsBuf);

    return ratioTmp;
  }
}
