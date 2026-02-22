package hu.mudlee.core.scene;

import hu.mudlee.core.GameTime;

public interface Scene {
    default void start() {}

    default void update(GameTime gameTime) {}

    default void resize(int width, int height) {}

    default void dispose() {}
}
