package hu.mudlee.sandbox;

import hu.mudlee.core.Application;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;

public class SandboxApplication {
    public static void main(String[] args) {
        final var windowPrefs = WindowPreferences
                .builder()
                .title("TESTING")
                .antialiasing(Antialiasing.OFF)
                .fullscreen(false)
                .build();

        var app = new Application(windowPrefs);
        app.start();
    }
}
