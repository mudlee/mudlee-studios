package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLGraphicsContext;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import hu.mudlee.core.render.vulkan.VulkanContext;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;

public class Renderer implements WindowEventListener {
  private final GraphicsContext context;
  private static Renderer instance;
  private static RenderBackend backend = RenderBackend.OPENGL;

  private Renderer() {
    context =
        switch (backend) {
          case OPENGL -> new OpenGLGraphicsContext(true);
          case VULKAN -> new VulkanContext(true);
        };
  }

  /**
   * Selects the rendering backend. Must be called before the first {@link #get()} call. Defaults to
   * {@link RenderBackend#OPENGL} if never called.
   */
  public static void configure(RenderBackend selectedBackend) {
    if (instance != null) {
      throw new IllegalStateException(
          "Renderer already initialised — configure() must be called before get()");
    }
    backend = selectedBackend;
  }

  public static RenderBackend activeBackend() {
    return backend;
  }

  public static Renderer get() {
    if (instance == null) {
      instance = new Renderer();
    }

    return instance;
  }

  @Override
  public void onWindowPrepared() {
    context.windowPrepared();
  }

  @Override
  public void onWindowCreated(long windowId, int width, int height, boolean vSync) {
    context.windowCreated(windowId, width, height, vSync);
  }

  @Override
  public void onWindowResized(int width, int height) {
    context.windowResized(width, height);
  }

  public static void renderRaw(
      VertexArray vao, Shader shader, RenderMode renderMode, PolygonMode polygonMode) {
    get().context.renderRaw(vao, shader, renderMode, polygonMode);
  }

  public static void setClearColor(Vector4f color) {
    get().context.setClearColor(color);
  }

  public static void setClearFlags(int mask) {
    get().context.setClearFlags(mask);
  }

  public static void swapBuffers(float frameTime) {
    get().context.swapBuffers(frameTime);
  }

  public static void clear() {
    get().context.clear();
  }

  public static void waitForGPU() {
    get().context.waitIdle();
  }

  public static void dispose() {
    get().context.dispose();
  }
}
