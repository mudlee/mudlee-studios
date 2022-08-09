module hu.mudlee.core {
    requires org.lwjgl;
    requires org.lwjgl.natives;
    requires org.lwjgl.opengl;
    requires org.lwjgl.opengl.natives;
    requires org.lwjgl.glfw;
    requires org.lwjgl.glfw.natives;
    requires org.slf4j;
    requires org.slf4j.simple;
    requires transitive org.joml;

    exports hu.mudlee.core;
    exports hu.mudlee.core.settings;
}