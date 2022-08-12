package hu.mudlee.core.input;

public class InputManager {
  private InputMultiplexer multiplexer;

  public void setMultiplexer(InputMultiplexer multiplexer) {
    this.multiplexer = multiplexer;
  }

  public void keyCallback(int key, int scancode, int action, int mods) {
    if(multiplexer != null) {
      multiplexer.keyCallback(key, scancode, action, mods);
    }
  }
}
