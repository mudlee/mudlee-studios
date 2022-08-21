package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLVertexBuffer;

public abstract class VertexBuffer {
	public static VertexBuffer create(float[] vertices, VertexBufferLayout layout, int bufferUsage) {
		return new OpenGLVertexBuffer(vertices, layout, bufferUsage);
	}

	public abstract int getId();

	public abstract int getLength();

	public abstract VertexBufferLayout getLayout();

	public abstract void bind();

	public abstract void unbind();

	public abstract void dispose();
}
