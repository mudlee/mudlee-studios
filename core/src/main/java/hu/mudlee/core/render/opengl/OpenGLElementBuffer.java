package hu.mudlee.core.render.opengl;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.types.IndexType;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OpenGL implementation of @ElementBuffer
 *
 * <p>Don't unbind before unbinding VAO, because it's state is not saved VBOs' state is saved
 * because of the call on glVertexAttribPointer
 */
public class OpenGLElementBuffer extends ElementBuffer {
    private static final Logger log = LoggerFactory.getLogger(OpenGLElementBuffer.class);
    private final int id;
    private final IndexType indexType;
    private int length;

    public OpenGLElementBuffer(int[] indices, int bufferUsage) {
        try (final var stack = stackPush()) {
            this.indexType = IndexType.INT;
            this.length = indices.length;
            id = glGenBuffers();
            final var buffer = stack.callocInt(indices.length).put(indices).flip();
            bind();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, bufferUsage);
            log.debug("ElementBuffer created {}", id);
        }
    }

    /** Dynamic constructor for short-index element buffers (e.g. Nuklear). */
    public OpenGLElementBuffer(int maxShortCount, boolean dynamic) {
        this.indexType = IndexType.SHORT;
        this.length = 0;
        id = glGenBuffers();
        bind();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) maxShortCount * Short.BYTES, GL_STREAM_DRAW);
        log.debug("ElementBuffer (dynamic short) created {}", id);
    }

    @Override
    public void update(ByteBuffer data, int byteCount) {
        this.length = byteCount / Short.BYTES;
        bind();
        var view = data.duplicate();
        view.limit(view.position() + byteCount);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0L, view);
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
    public IndexType getIndexType() {
        return indexType;
    }

    @Override
    public void bind() {
        log.trace("Bind element buffer {}", id);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, id);
    }

    @Override
    public void unbind() {
        log.trace("Unbind element buffer {}", id);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose() {
        log.trace("Dispose element buffer {}", id);
        glDeleteBuffers(id);
    }
}
