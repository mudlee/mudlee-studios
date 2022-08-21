package hu.mudlee.core.scene;

public interface Scene {
	default void start() {}

	default void update(float deltaTime) {}

	default void resize(int width, int height) {}

	default void dispose() {}
}
