package hu.mudlee.core;

public interface LifeCycleListener {
  default void onCreated() {}

  default void onUpdate(float delta) {}

  default void onResize(int width, int height) {}

  default void onDispose() {}
}
