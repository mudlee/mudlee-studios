package hu.mudlee.core.render.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface Camera {
  boolean update();

  void resize(int width, int height);

  void setRotation(Vector3f rotation);

  void setPosition(Vector3f position);

  Vector3f getPosition();

  Vector3f getRotation();

  Matrix4f getViewMatrix();

  Matrix4f getProjectionMatrix();
}
