package hu.mudlee.core;

import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.systems.RawRenderableSystem;
import hu.mudlee.core.input.InputSystem;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.scene.SceneManager;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;
import hu.mudlee.core.window.WindowEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Game implements WindowEventListener {

    private static final Logger log = LoggerFactory.getLogger(Game.class);
    private static final float TARGET_ELAPSED_SECONDS = 1f / 60f;

    protected GraphicsDeviceManager gdm;
    protected GraphicsDevice graphicsDevice;
    protected ContentManager content;

    protected Game() {}

    public final void run() {
        if (gdm == null) {
            gdm = new GraphicsDeviceManager();
        }

        Renderer.configure(gdm.getPreferredBackend());

        Window.setPreferences(WindowPreferences.builder()
                .title(gdm.getTitle())
                .width(gdm.getPreferredBackBufferWidth())
                .height(gdm.getPreferredBackBufferHeight())
                .vSync(gdm.isVSync())
                .fullscreen(gdm.isFullscreen())
                .antialiasing(Antialiasing.OFF)
                .build());

        Window.addListener(Renderer.get());
        Window.addListener(this);
        ECS.addSystem(new RawRenderableSystem());

        Window.create();
        graphicsDevice = new GraphicsDevice();

        if (content == null) {
            content = new ContentManager("");
        }

        initialize();
        loadContent();

        loop();

        log.info("Game is shutting down");
        Renderer.waitForGPU();
        unloadContent();
        SceneManager.onDispose();
        Renderer.dispose();
        Window.remove();
        log.info("Terminated");
    }

    public final void exit() {
        Window.close();
    }

    @Override
    public void onWindowResized(int width, int height) {
        SceneManager.onWindowResized(width, height);
    }

    protected void initialize() {}

    protected void loadContent() {}

    protected void update(GameTime gameTime) {}

    protected void draw(GameTime gameTime) {}

    protected void unloadContent() {}

    private void loop() {
        var beginTime = Time.getTime();
        var totalTime = 0f;
        float endTime;
        var deltaTime = 0f;
        var gameTime = new GameTime(0f, 0f, false);

        while (!Window.shouldClose()) {
            InputSystem.update();
            Window.pollEvents();

            if (deltaTime >= 0f) {
                totalTime += deltaTime;
                gameTime.set(deltaTime, totalTime, deltaTime > TARGET_ELAPSED_SECONDS);
                SceneManager.onUpdate(gameTime);
                update(gameTime);
                ECS.update(deltaTime);
                draw(gameTime);
            }

            Renderer.swapBuffers(deltaTime);
            endTime = Time.getTime();
            deltaTime = endTime - beginTime;
            beginTime = endTime;
        }
    }
}
