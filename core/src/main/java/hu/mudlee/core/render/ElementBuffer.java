package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLElementBuffer;

public abstract class ElementBuffer {
	public static ElementBuffer create(int[] indices, int bufferUsage) {
		return new OpenGLElementBuffer(indices, bufferUsage);
	}

	public abstract int getId();

	public abstract int getLength();

	public abstract void bind();

	public abstract void unbind();

	public abstract void dispose();
}
