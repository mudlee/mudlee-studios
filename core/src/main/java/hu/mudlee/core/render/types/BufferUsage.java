package hu.mudlee.core.render.types;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL41.GL_STATIC_DRAW;

public class BufferUsage {
    public static final int STATIC_DRAW = GL_STATIC_DRAW;
    public static final int STREAM_DRAW = GL_STREAM_DRAW;
    public static final int DYNAMIC_DRAW = GL_DYNAMIC_DRAW;
}
