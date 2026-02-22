package hu.mudlee.core.render.opengl;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenGLVertexBuffer extends VertexBuffer {
    private static final Logger log = LoggerFactory.getLogger(OpenGLVertexBuffer.class);
    private final int id;
    private final VertexBufferLayout layout;
    private int length;

    public OpenGLVertexBuffer(float[] vertices, VertexBufferLayout layout, int bufferUsage) {
        try (final var stack = stackPush()) {
            this.layout = layout;
            length = vertices.length;
            id = glGenBuffers();
            bind();
            final var buffer = stack.callocFloat(vertices.length).put(vertices).flip();
            glBufferData(GL_ARRAY_BUFFER, buffer, bufferUsage);
            unbind();
            log.debug("VertexBuffer created ID:{}", id);
        }
    }

    /** Dynamic constructor: allocates a DYNAMIC_DRAW buffer of {@code maxFloats} capacity. */
    public OpenGLVertexBuffer(VertexBufferLayout layout, int maxFloats) {
        this.layout = layout;
        this.length = 0;
        id = glGenBuffers();
        bind();
        glBufferData(GL_ARRAY_BUFFER, (long) maxFloats * Float.BYTES, GL_DYNAMIC_DRAW);
        unbind();
        log.debug("VertexBuffer (dynamic) created ID:{}", id);
    }

    @Override
    public void update(float[] data, int floatCount) {
        this.length = floatCount;
        bind();
        try (var stack = stackPush()) {
            var buffer = stack.mallocFloat(floatCount).put(data, 0, floatCount).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0L, buffer);
        }
        unbind();
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public VertexBufferLayout getLayout() {
        return this.layout;
    }

    @Override
    public void bind() {
        log.trace("Bind vertex buffer ID:{}", id);
        glBindBuffer(GL_ARRAY_BUFFER, id);
    }

    @Override
    public void unbind() {
        log.trace("Unbind vertex buffer ID:{}", id);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose() {
        log.trace("Bind vertex buffer ID:{}", id);
        glDeleteBuffers(id);
    }
}
