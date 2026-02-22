package hu.mudlee.sandbox;

import hu.mudlee.core.Game;
import hu.mudlee.core.GraphicsDeviceManager;
import hu.mudlee.core.ScreenManager;
import hu.mudlee.core.render.RenderBackend;

public class SandboxApplication extends Game {

    public SandboxApplication() {
        gdm = new GraphicsDeviceManager()
                .setTitle("TESTING")
                .setPreferredBackBufferWidth(1920)
                .setPreferredBackBufferHeight(1080)
                .setVSync(true)
                .setFullscreen(false)
                .setPreferredBackend(RenderBackend.VULKAN);
    }

    @Override
    protected void loadContent() {
        var screenManager = new ScreenManager();
        components.add(screenManager);
        screenManager.set(new PlayerScreen(this, graphicsDevice));
    }

    public static void main(String[] args) {
        new SandboxApplication().run();
    }
}
