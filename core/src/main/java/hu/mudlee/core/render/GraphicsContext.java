package hu.mudlee.core.render;

import hu.mudlee.core.Disposable;
import org.joml.Vector4f;

public interface GraphicsContext extends Disposable {
  void windowPrepared();

  void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync);

  void setClearFlags(int mask);

  void setClearColor(Vector4f color);

  void clear();

  void renderRaw(VertexArray vao, Shader shader, int renderMode, int polygonMode);

  void swapBuffers(float frameTime);

  void windowResized(int newWidth, int newHeight);
}
