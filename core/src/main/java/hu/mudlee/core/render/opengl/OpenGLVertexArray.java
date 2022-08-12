package hu.mudlee.core.render.opengl;

import hu.mudlee.core.render.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.opengl.GL41.*;

public class OpenGLVertexArray extends VertexArray {
	private static final Logger log = LoggerFactory.getLogger(OpenGLVertexArray.class);
	private final int id;
	private final List<VertexBuffer> vertexBuffers = new ArrayList<>();
	private IndexBuffer indexBuffer;
	private int instanceCount;
	private boolean instanced;

	public OpenGLVertexArray() {
		id = glGenVertexArrays();
		log.debug("VertexArray created ID:{}", id);
	}

	@Override
	public void bind() {
		log.trace("Bind vertex array ID:{}", id);
		glBindVertexArray(id);
	}

	@Override
	public void unbind() {
		log.trace("Unbind vertex array ID:{}", id);
		glBindVertexArray(0);
	}

	@Override
	public void addVertexBuffer(VertexBuffer buffer) {
		log.trace("Add vertex buffer ID:{} to vertex array ID:{}", buffer.getId(), id);
		bind();
		if (buffer.getLayout() == null) {
			throw new RuntimeException("VertexBuffer does not define its layout");
		}

		buffer.bind();
		for (VertexLayoutAttribute attribute : buffer.getLayout().attributes()) {
			glEnableVertexAttribArray(attribute.getIndex());
			glVertexAttribPointer(
				attribute.getIndex(),
				attribute.getDataSize(),
				attribute.getDataType(),
				attribute.isNormalized(),
				attribute.getStride(),
				attribute.getOffset()
			);

			if(attribute instanceof VertexLayoutInstancedAttribute instancedAttribute) {
				glVertexAttribDivisor(attribute.getIndex(), instancedAttribute.getDivisor());
				instanced = true;
			}
		}
		buffer.unbind();
		unbind();
		vertexBuffers.add(buffer);
	}

	@Override
	public void setIndexBuffer(IndexBuffer buffer) {
		log.trace("Add index buffer ID:{} to vertex array ID:{}", buffer.getId(), id);
		bind();
		buffer.bind();
		unbind();
		indexBuffer = buffer;
	}

	@Override
	public void setInstanceCount(int count) {
		instanceCount = count;
	}

	@Override
	public List<VertexBuffer> getVertexBuffers() {
		return vertexBuffers;
	}

	@Override
	public Optional<IndexBuffer> getIndexBuffer() {
		return Optional.ofNullable(indexBuffer);
	}

	@Override
	public int getInstanceCount() {
		return instanceCount;
	}

	@Override
	public boolean isInstanced() {
		return instanced;
	}

	@Override
	public void dispose() {
		log.trace("Dispose vertex array ID:{}", id);
		glDeleteVertexArrays(id);
	}
}
