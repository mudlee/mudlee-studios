package hu.mudlee.core;

import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.systems.RawRenderableSystem;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.types.BufferBitTypes;
import hu.mudlee.core.scene.SceneManager;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Game implements WindowEventListener {

    private static final Logger log = LoggerFactory.getLogger(Game.class);
    private static final float TARGET_ELAPSED_SECONDS = 1f / 60f;

    protected GraphicsDeviceManager gdm;

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
        Renderer.setClearColor(new Vector4f(1f, 1f, 1f, 1f));

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
        Renderer.setClearFlags(BufferBitTypes.COLOR);

        var beginTime = Time.getTime();
        var totalTime = 0f;
        float endTime;
        var deltaTime = -1.0f;

        while (!Window.shouldClose()) {
            Window.pollEvents();
            Renderer.clear();

            if (deltaTime >= 0) {
                totalTime += deltaTime;
                var gameTime = new GameTime(deltaTime, totalTime, deltaTime > TARGET_ELAPSED_SECONDS);
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
