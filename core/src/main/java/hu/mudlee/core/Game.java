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
    private static final long MAX_DELTA_NANOS = 100_000_000L;

    private final List<GameModule> modules = new ArrayList<>();
    private final List<GameModule> pendingModuleAdds = new ArrayList<>();
    private final List<GameModule> pendingModuleRemoves = new ArrayList<>();
    private boolean iteratingModules;
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

    public void addModule(GameModule module) {
        if (iteratingModules) {
            pendingModuleAdds.add(module);
        } else {
            modules.add(module);
        }
    }

    public void removeModule(GameModule module) {
        if (iteratingModules) {
            pendingModuleRemoves.add(module);
        } else {
            modules.remove(module);
        }
    }

    @Override
    public void onWindowResized(int width, int height) {
        for (var module : modules) {
            module.resize(width, height);
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
        var vSync = gdm.isVSync();

        while (!Window.shouldClose()) {
            InputSystem.update();
            Window.pollEvents();

            var clampedDeltaNanos = Math.min(deltaNanos, MAX_DELTA_NANOS);
            totalNanos += clampedDeltaNanos;
            var deltaSeconds = (float) (clampedDeltaNanos * 1e-9);
            var totalSeconds = (float) (totalNanos * 1e-9);
            gameTime.set(deltaSeconds, totalSeconds, clampedDeltaNanos > TARGET_ELAPSED_NANOS);
            update(gameTime);
            iteratingModules = true;
            for (var module : modules) {
                module.update(gameTime);
            }
            draw(gameTime);
            for (var module : modules) {
                module.draw(gameTime);
            }
            iteratingModules = false;
            applyPendingModuleChanges();
            graphicsDevice.present(deltaSeconds);

            // When vSync is enabled, present already blocks for the display refresh interval,
            // so no additional sleep is needed. Sleep only when running uncapped to avoid
            // Thread.sleep(1) overshooting on Windows and pulling FPS below 60.
            if (!vSync) {
                var elapsedNanos = System.nanoTime() - beginNanos;
                while (elapsedNanos < TARGET_ELAPSED_NANOS) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    elapsedNanos = System.nanoTime() - beginNanos;
                }
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
        for (var module : modules) {
            try {
                module.dispose();
            } catch (Exception e) {
                log.error("Error disposing module: {}", module.getClass().getSimpleName(), e);
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

    private void applyPendingModuleChanges() {
        if (!pendingModuleAdds.isEmpty()) {
            modules.addAll(pendingModuleAdds);
            pendingModuleAdds.clear();
        }
        if (!pendingModuleRemoves.isEmpty()) {
            modules.removeAll(pendingModuleRemoves);
            pendingModuleRemoves.clear();
        }
    }
}
