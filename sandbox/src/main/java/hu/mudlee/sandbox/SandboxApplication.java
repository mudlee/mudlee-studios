package hu.mudlee.sandbox;

import hu.mudlee.core.Game;
import hu.mudlee.core.GraphicsDeviceManager;
import hu.mudlee.core.ScreenManager;
import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.ui.DebugStatsComponent;
import hu.mudlee.core.ui.UIService;

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
        addService(screenManager);
        screenManager.set(new PlayerScene(this, graphicsDevice));

        // UIService must be added after ScreenManager so it renders on top of the scene.
        var uiService = new UIService();
        addService(uiService);
        uiService.getCanvas().create().addComponent(new DebugStatsComponent(uiService.getDefaultFont()));
    }

    public static void main(String[] args) {
        new SandboxApplication().run();
    }
}
