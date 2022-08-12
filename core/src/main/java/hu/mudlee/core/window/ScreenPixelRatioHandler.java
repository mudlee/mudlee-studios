package hu.mudlee.core.window;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;

public class ScreenPixelRatioHandler {
	private static final Logger log = LoggerFactory.getLogger(ScreenPixelRatioHandler.class);

	public static int set(long windowId, GLFWVidMode vidMode) {
		// https://en.wikipedia.org/wiki/4K_resolution
		final int uhdMinWidth = 3840;
		final int uhdMinHeight = 1716;
		final boolean UHD = vidMode.width() >= uhdMinWidth && vidMode.height() >= uhdMinHeight;
		log.debug("Screen is {}x{}, UHD: {}", vidMode.width(), vidMode.height(), UHD);

		// Check if the monitor is 4K
		if (UHD) {
			log.debug("Screen pixel ratio has been calculated to: 2");
			return 2;
		}

		IntBuffer widthScreenCoordBuf = MemoryUtil.memAllocInt(1);
		IntBuffer heightScreenCoordBuf = MemoryUtil.memAllocInt(1);
		IntBuffer widthPixelsBuf = MemoryUtil.memAllocInt(1);
		IntBuffer heightPixelsBuf = MemoryUtil.memAllocInt(1);

		glfwGetWindowSize(windowId, widthScreenCoordBuf, heightScreenCoordBuf);
		glfwGetFramebufferSize(windowId, widthPixelsBuf, heightPixelsBuf);

		final int screenPixelRatio = (int) Math.floor((float) widthPixelsBuf.get() / (float) widthScreenCoordBuf.get());
		log.debug("Screen pixel ratio has been calculated to: {}", screenPixelRatio);

		MemoryUtil.memFree(widthScreenCoordBuf);
		MemoryUtil.memFree(heightScreenCoordBuf);
		MemoryUtil.memFree(widthPixelsBuf);
		MemoryUtil.memFree(heightPixelsBuf);

		return screenPixelRatio;
	}
}
