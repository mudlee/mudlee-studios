package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLElementBuffer;
import hu.mudlee.core.render.vulkan.VulkanIndexBuffer;

public abstract class ElementBuffer {
	public static ElementBuffer create(int[] indices, int bufferUsage) {
		return switch (Renderer.activeBackend()) {
			case OPENGL -> new OpenGLElementBuffer(indices, bufferUsage);
			case VULKAN -> new VulkanIndexBuffer(indices);
		};
	}

	public abstract int getId();

	public abstract int getLength();

	public abstract void bind();

	public abstract void unbind();

	public abstract void dispose();
}
