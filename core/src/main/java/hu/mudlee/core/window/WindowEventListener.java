package hu.mudlee.core.window;

public interface WindowEventListener {
  default void onWindowPrepared() {}

  default void onWindowCreated(long windowId, int width, int height, boolean vSync) {}

  default void onWindowResized(int width, int height) {}
}
