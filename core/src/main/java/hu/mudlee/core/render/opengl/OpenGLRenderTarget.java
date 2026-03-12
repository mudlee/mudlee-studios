package hu.mudlee.core.render.opengl;

import static org.lwjgl.opengl.GL41.*;

import hu.mudlee.core.render.RenderTarget;
import hu.mudlee.core.render.texture.Texture2D;

/** OpenGL render target backed by a framebuffer object (FBO). */
public final class OpenGLRenderTarget extends RenderTarget {

    private int fboId;
    private int depthRboId;
    private OpenGLTexture2D colorTexture;
    private int width;
    private int height;

    public OpenGLRenderTarget(int width, int height) {
        this.width = width;
        this.height = height;
        create();
    }

    int fboId() {
        return fboId;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Texture2D getColorTexture() {
        return colorTexture;
    }

    @Override
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = newWidth;
        height = newHeight;
        deleteGpuObjects();
        create();
    }

    @Override
    public void dispose() {
        deleteGpuObjects();
    }

    private void create() {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        colorTexture = new OpenGLTexture2D(texId, width, height);

        depthRboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texId, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRboId);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("OpenGLRenderTarget framebuffer is incomplete");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void deleteGpuObjects() {
        colorTexture.dispose();
        glDeleteRenderbuffers(depthRboId);
        glDeleteFramebuffers(fboId);
    }
}
