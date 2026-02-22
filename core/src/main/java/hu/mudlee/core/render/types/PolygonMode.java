package hu.mudlee.core.render.types;

import static org.lwjgl.opengl.GL41.GL_FILL;
import static org.lwjgl.opengl.GL41.GL_LINE;

public enum PolygonMode {
    LINE(GL_LINE),
    FILL(GL_FILL);

    public final int glRef;

    PolygonMode(int glRef) {
        this.glRef = glRef;
    }
}
