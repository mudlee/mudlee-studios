package hu.mudlee.core;

import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.systems.RawRenderableSystem;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.types.BufferBitTypes;
import hu.mudlee.core.scene.Scene;
import hu.mudlee.core.scene.SceneManager;
import hu.mudlee.core.settings.WindowPreferences;
import hu.mudlee.core.window.Window;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Application implements WindowEventListener {
	private static final Logger log = LoggerFactory.getLogger(Application.class);
	private static Application instance;

	public static Application get() {
		if (instance == null) {
			instance = new Application();
		}

		return instance;
	}

	private Application() {
	}

	@Override
	public void onWindowResized(int width, int height) {
		SceneManager.onWindowResized(width, height);
	}

	public static void start(WindowPreferences windowPreferences, Scene startingScene) {
		Window.setPreferences(windowPreferences);
		Window.addListener(Renderer.get());
		Window.addListener(get());
		ECS.addSystem(new RawRenderableSystem());

		Window.create();
		Renderer.setClearColor(new Vector4f(1f, 1f, 1f, 1f));
		SceneManager.setScreen(startingScene);

		get().loop();

		log.info("Application is shutting down");
		SceneManager.onDispose();
		Renderer.dispose();
		Window.remove();
		log.info("Terminated");
	}

	public static void stop() {
		Window.close();
	}

	private void loop() {
		Renderer.setClearFlags(BufferBitTypes.COLOR);

		float beginTime = Time.getTime();
		float endTime;
		float deltaTime = -1.0f;

		while (!Window.shouldClose()) {
			Window.pollEvents();

			Renderer.clear();

			if(deltaTime >= 0) {
				SceneManager.onUpdate(deltaTime);
				ECS.update(deltaTime);
			}

			Renderer.swapBuffers(deltaTime);
			endTime = Time.getTime();
			deltaTime = endTime - beginTime;
			beginTime = endTime;
		}
	}
}
