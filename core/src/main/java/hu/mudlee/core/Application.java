package hu.mudlee.core;

import com.badlogic.ashley.core.EntitySystem;
import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.systems.RawRenderableSystem;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.types.BufferBitTypes;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Application implements WindowEventListener {
	public final Renderer renderer;
	protected final Window window;
	private final LifeCycleListener game;
	private static final Logger log = LoggerFactory.getLogger(Application.class);
	private float deltaTime;
	private float frameTime;

	public Application(LifeCycleListener game, WindowPreferences windowPreferences) {
		this.game = game;
		renderer = new Renderer(true);
		window = new Window(windowPreferences, GameEngine.input, List.of(renderer, this));

		if(GameEngine.app != null) {
			throw new RuntimeException("Cannot run multiple applications");
		}

		final var systems = new ArrayList<EntitySystem>();
		systems.add(new RawRenderableSystem(renderer));

		GameEngine.ecs = new ECS(systems);
		GameEngine.app = this;
	}

	@Override
	public void onWindowResized(int width, int height) {
		game.onResize(width,height);
	}

	public void start() {
		window.create();
		game.onCreated();

		loop();

		log.info("Application is shutting down");
		game.onDispose();
		renderer.dispose();
		window.dispose();
		log.info("Terminated");
	}

	public void stop() {
		window.close();
	}

	private void loop() {
		long lastTime = System.nanoTime();
		renderer.setClearColor(new Vector4f(1f, 1f, 1f, 1f));
		renderer.setClearFlags(BufferBitTypes.COLOR);

		while (!window.shouldClose()) {
			long now = System.nanoTime();
			long frameTimeNanos = now - lastTime;
			deltaTime = frameTimeNanos / 1_000_000_000f;
			lastTime = now;

			renderer.clear();
			GameEngine.ecs.update(deltaTime);
			game.onUpdate(deltaTime);

			renderer.swapBuffers(deltaTime);
			window.pollEvents();
			frameTime = (System.nanoTime() - now) / 1_000_000f;
		}
	}
}
