package hu.mudlee.core.input;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public class InputMultiplexer {
  private final List<InputProcessor> processors = new ArrayList<>();

  public int addProcessor(InputProcessor processor) {
    processors.add(processor);
    return processors.size() - 1;
  }

  public void removeProcessor(int index) {
    processors.remove(index);
  }

  public void keyCallback(int key, int scancode, int action, int mods) {
    switch (action) {
      case GLFW_PRESS:
        for (InputProcessor processor : processors) {
          processor.onKeyPress(key);
        }
        break;
      case GLFW_RELEASE:
        for (InputProcessor processor : processors) {
          processor.onKeyReleased(key);
        }
        break;
    }
  }
}
