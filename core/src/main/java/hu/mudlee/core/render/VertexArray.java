package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLVertexArray;
import hu.mudlee.core.render.vulkan.VulkanVertexArray;

import java.util.List;
import java.util.Optional;

public abstract class VertexArray {
	public static VertexArray create() {
		return switch (Renderer.activeBackend()) {
			case OPENGL -> new OpenGLVertexArray();
			case VULKAN -> new VulkanVertexArray();
		};
	}

	public abstract void bind();

	public abstract void unbind();

	public abstract void addVBO(VertexBuffer buffer);

	public abstract void setEBO(ElementBuffer elementBuffer);

	public abstract void setInstanceCount(int count);

	public abstract List<VertexBuffer> getVBOs();

	public abstract Optional<ElementBuffer> getEBO();

	public abstract int getInstanceCount();

	public abstract boolean isInstanced();

	public abstract void dispose();
}
