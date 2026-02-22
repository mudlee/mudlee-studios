package hu.mudlee.core.scene;

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
        get().current.resize(width, height);
    }

    public static void onStarted() {
        get().current.start();
    }

    public static void onDispose() {
        get().current.dispose();
    }

    public static void onUpdate(float deltaTime) {
        get().current.update(deltaTime);
    }
}
