package hu.mudlee.core.scene;

import hu.mudlee.core.GameTime;

public class SceneManager {
    private static SceneManager instance;
    private Scene current;

    private SceneManager() {}

    public static SceneManager get() {
        if (SceneManager.instance == null) {
            SceneManager.instance = new SceneManager();
        }

        return SceneManager.instance;
    }

    public static void setScreen(Scene scene) {
        if (get().current != null) {
            get().current.dispose();
        }

        get().current = scene;
        get().current.start();
    }

    public static void onWindowResized(int width, int height) {
        if (get().current != null) {
            get().current.resize(width, height);
        }
    }

    public static void onStarted() {
        if (get().current != null) {
            get().current.start();
        }
    }

    public static void onDispose() {
        if (get().current != null) {
            get().current.dispose();
        }
    }

    public static void onUpdate(GameTime gameTime) {
        if (get().current != null) {
            get().current.update(gameTime);
        }
    }
}
