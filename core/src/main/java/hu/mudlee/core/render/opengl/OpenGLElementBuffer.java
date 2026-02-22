package hu.mudlee.core.render.opengl;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import hu.mudlee.core.render.ElementBuffer;
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
  private final int length;

  public OpenGLElementBuffer(int[] indices, int bufferUsage) {
    try (final var stack = stackPush()) {
      this.length = indices.length;
      id = glGenBuffers();
      final var buffer = stack.callocInt(indices.length).put(indices).flip();
      bind();
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, bufferUsage);
      log.debug("ElementBuffer created {}", id);
    }
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
