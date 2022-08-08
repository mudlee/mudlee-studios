package hu.mudlee.core;

import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;

public class Application {
    private final Window window;

    public Application(WindowPreferences windowPreferences) {
        this.window = new Window(windowPreferences);
    }

    public void start() {
        window.create();
        loop();
    }

    private void loop() {
        while (!window.shouldClose()) {
            window.pollEvents();
        }
    }
}
