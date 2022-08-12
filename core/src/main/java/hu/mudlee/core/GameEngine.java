package hu.mudlee.core;

import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.input.InputManager;

public class GameEngine {
	public static Application app;
	public static InputManager input = new InputManager();
	public static ECS ecs;
}
