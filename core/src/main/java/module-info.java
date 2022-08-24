module hu.mudlee.core {
	requires org.lwjgl;
	requires org.lwjgl.natives;
	requires org.lwjgl.opengl;
	requires org.lwjgl.opengl.natives;
	requires transitive org.lwjgl.glfw;
	requires org.lwjgl.glfw.natives;
	requires org.slf4j;
	requires org.slf4j.simple;
	requires org.lwjgl.stb;
	requires org.lwjgl.stb.natives;
	requires transitive org.joml;
	requires transitive ashley;

	exports hu.mudlee.core;
	exports hu.mudlee.core.ecs;
	exports hu.mudlee.core.ecs.entities;
	exports hu.mudlee.core.input;
	exports hu.mudlee.core.render;
	exports hu.mudlee.core.render.camera;
	exports hu.mudlee.core.render.texture;
	exports hu.mudlee.core.render.types;
	exports hu.mudlee.core.scene;
	exports hu.mudlee.core.settings;
	exports hu.mudlee.core.window;
}