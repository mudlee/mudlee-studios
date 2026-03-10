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
    requires org.lwjgl.vulkan;
    requires transitive org.joml;
    requires java.management;

    exports hu.mudlee.core;
    exports hu.mudlee.core.content;
    exports hu.mudlee.core.ecs;
    exports hu.mudlee.core.ecs.component;
    exports hu.mudlee.core.ecs.system;
    exports hu.mudlee.core.input;
    exports hu.mudlee.core.render;
    exports hu.mudlee.core.render.camera;
    exports hu.mudlee.core.render.animation;
    exports hu.mudlee.core.render.texture;
    exports hu.mudlee.core.render.types;
    exports hu.mudlee.core.render.font;
    exports hu.mudlee.core.settings;
    exports hu.mudlee.core.window;
    exports hu.mudlee.core.render.vulkan;
    exports hu.mudlee.core.ui;
}
