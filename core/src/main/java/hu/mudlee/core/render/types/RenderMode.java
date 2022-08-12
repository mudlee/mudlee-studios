package hu.mudlee.core.render.types;

import static org.lwjgl.opengl.GL41.*;

public enum RenderMode {
	TRIANGLES(GL_TRIANGLES),
	LINES(GL_LINES),
	POINTS(GL_POINTS);

	public final int glRef;

	RenderMode(int glRef) {
		this.glRef = glRef;
	}
}