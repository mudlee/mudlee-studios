package hu.mudlee.sandbox;

import hu.mudlee.core.Application;
import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.sandbox.scenes.GameScene;

public class SandboxApplication {
  public static void main(String[] args) {
    Renderer.configure(RenderBackend.VULKAN);

    Application.start(
        WindowPreferences.builder()
            .title("TESTING")
            .antialiasing(Antialiasing.OFF)
            .fullscreen(false)
            .vSync(true)
            .width(1920)
            .height(1080)
            .build(),
        new GameScene());
  }
}
