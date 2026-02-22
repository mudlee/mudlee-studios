package hu.mudlee.sandbox.scenes;

import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.scene.Scene;
import org.joml.Vector4f;

public class OtherScene implements Scene {
  @Override
  public void start() {
    System.out.println("HELLO OTHERSCENE");
    Renderer.setClearColor(new Vector4f(1, 1, 1, 1));
  }
}
