package hu.mudlee.core;

import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.input.InputSystem;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;
import hu.mudlee.core.window.WindowEventListener;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Game implements WindowEventListener {

    private static final Logger log = LoggerFactory.getLogger(Game.class);
    private static final long TARGET_ELAPSED_NANOS = 1_000_000_000L / 60;

    public final List<GameService> components = new ArrayList<>();
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

        Window.create();
        graphicsDevice = new GraphicsDevice();

        if (content == null) {
            content = new ContentManager("");
        }

        initialize();
        loadContent();

        try {
            loop();
        } finally {
            shutdown();
        }
    }

    public final void exit() {
        Window.close();
    }

    @Override
    public void onWindowResized(int width, int height) {
        for (var component : components) {
            component.resize(width, height);
        }
    }

    protected void initialize() {}

    protected void loadContent() {}

    protected void update(GameTime gameTime) {}

    protected void draw(GameTime gameTime) {}

    protected void unloadContent() {}

    private void loop() {
        var beginNanos = System.nanoTime();
        var totalNanos = 0L;
        var deltaNanos = 0L;
        var gameTime = new GameTime(0f, 0f, false);

        while (!Window.shouldClose()) {
            InputSystem.update();
            Window.pollEvents();

            if (deltaNanos >= 0L) {
                totalNanos += deltaNanos;
                var deltaSeconds = (float) (deltaNanos * 1e-9);
                var totalSeconds = (float) (totalNanos * 1e-9);
                gameTime.set(deltaSeconds, totalSeconds, deltaNanos > TARGET_ELAPSED_NANOS);
                update(gameTime);
                for (var component : components) {
                    component.update(gameTime);
                }
                draw(gameTime);
                for (var component : components) {
                    component.draw(gameTime);
                }
                Renderer.swapBuffers(deltaSeconds);
            }

            var elapsedNanos = System.nanoTime() - beginNanos;
            while (elapsedNanos < TARGET_ELAPSED_NANOS) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                elapsedNanos = System.nanoTime() - beginNanos;
            }

            var endNanos = System.nanoTime();
            deltaNanos = endNanos - beginNanos;
            beginNanos = endNanos;
        }
    }

    private void shutdown() {
        log.info("Game is shutting down");
        try {
            Renderer.waitForGPU();
        } catch (Exception e) {
            log.error("Error waiting for GPU", e);
        }
        try {
            unloadContent();
        } catch (Exception e) {
            log.error("Error unloading content", e);
        }
        for (var component : components) {
            try {
                component.dispose();
            } catch (Exception e) {
                log.error("Error disposing component: {}", component.getClass().getSimpleName(), e);
            }
        }
        try {
            Renderer.dispose();
        } catch (Exception e) {
            log.error("Error disposing renderer", e);
        }
        try {
            Window.remove();
        } catch (Exception e) {
            log.error("Error removing window", e);
        }
        log.info("Terminated");
    }
}
