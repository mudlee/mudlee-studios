package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLIndexBuffer;

public abstract class IndexBuffer {
	public static IndexBuffer create(int[] indices) {
		return new OpenGLIndexBuffer(indices);
	}

	public abstract int getId();

	public abstract int getLength();

	public abstract void bind();

	public abstract void unbind();

	public abstract void dispose();
}
