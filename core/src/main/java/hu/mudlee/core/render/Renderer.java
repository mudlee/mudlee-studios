package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLGraphicsContext;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;

public class Renderer implements WindowEventListener {
  private final GraphicsContext context;

  public Renderer(boolean debug) {
    context = new OpenGLGraphicsContext(debug);
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

  public void renderRaw(VertexArray vao, Shader shader, int renderMode, int polygonMode) {
    context.renderRaw(vao, shader, renderMode, polygonMode);
  }

  public void setClearColor(Vector4f color) {
    context.setClearColor(color);
  }

  public void setClearFlags(int mask) {
    context.setClearFlags(mask);
  }

  public void swapBuffers(float frameTime) {
    context.swapBuffers(frameTime);
  }

  public void clear() {
    context.clear();
  }

  public void dispose() {
    context.dispose();
  }
}
