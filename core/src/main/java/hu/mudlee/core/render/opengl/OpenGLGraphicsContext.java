package hu.mudlee.core.render.opengl;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL41.*;

import hu.mudlee.core.render.GraphicsContext;
import hu.mudlee.core.render.RenderTarget;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.types.BlendFactor;
import hu.mudlee.core.render.types.IndexType;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenGLGraphicsContext implements GraphicsContext {
    private static final Logger log = LoggerFactory.getLogger(OpenGLGraphicsContext.class);
    private final boolean debug;
    private int clearFlags = 0;
    private long windowId;
    private int windowWidth;
    private int windowHeight;
    private PolygonMode prevPolygonMode = PolygonMode.FILL;
    private String rendererInfo = "";
    private boolean blendEnabled = false;
    private BlendFactor blendSrc = null;
    private BlendFactor blendDst = null;

    public OpenGLGraphicsContext(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void windowPrepared() {
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        if (debug) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        }
    }

    @Override
    public void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync) {
        log.debug("Initializing OpenGL context...");
        this.windowId = windowId;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;

        glfwMakeContextCurrent(this.windowId);

        createCapabilities();

        if (debug) {
            GLUtil.setupDebugMessageCallback();
            if (GL.getCapabilities().OpenGL43) {
                GL43.glDebugMessageControl(
                        GL_DONT_CARE, GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, (int[]) null, false);
            }
        }

        log.debug("Initialized");
        log.debug("\tOpenGL Vendor: {}", glGetString(GL_VENDOR));
        log.debug("\tVersion: {}", glGetString(GL_VERSION));
        log.debug("\tRenderer: {}", glGetString(GL_RENDERER));
        log.debug("\tShading Language Version: {}", glGetString(GL_SHADING_LANGUAGE_VERSION));
        log.debug("\tVsync: {}", vSync);

        rendererInfo = glGetString(GL_RENDERER) + " (OpenGL)";
        glfwSwapInterval(vSync ? GLFW_TRUE : GLFW_FALSE);
    }

    @Override
    public void setClearFlags(int mask) {
        this.clearFlags = mask;
    }

    @Override
    public void setClearColor(Vector4f color) {
        glClearColor(color.x, color.y, color.z, color.w);
    }

    @Override
    public void clear() {
        glClear(clearFlags);
    }

    /**
     * A VAO with multiple VBOs describes a single mesh whose attributes are split across streams
     * (e.g. positions in VBO 0, UVs in VBO 1). The GPU reads all streams in parallel during one
     * draw call, so only a single {@code glDrawArrays} / {@code glDrawElements} is ever needed.
     */
    @Override
    public void renderRaw(VertexArray vao, Shader shader, RenderMode renderMode, PolygonMode polygonMode) {
        shader.bind();
        vao.bind();

        if (prevPolygonMode != polygonMode) {
            glPolygonMode(GL41.GL_FRONT_AND_BACK, polygonMode.glRef);
            prevPolygonMode = polygonMode;
        }

        if (vao.isInstanced()) {
            if (vao.getEBO().isPresent()) {
                glDrawElementsInstanced(
                        renderMode.glRef, vao.getEBO().get().getLength(), GL_UNSIGNED_INT, 0, vao.getInstanceCount());
            } else {
                glDrawArraysInstanced(
                        renderMode.glRef, 0, vertexCount(vao.getVBOs().get(0)), vao.getInstanceCount());
            }
        } else {
            if (vao.getEBO().isPresent()) {
                glDrawElements(renderMode.glRef, vao.getEBO().get().getLength(), GL_UNSIGNED_INT, 0);
            } else {
                glDrawArrays(renderMode.glRef, 0, vertexCount(vao.getVBOs().get(0)));
            }
        }

        vao.unbind();
        shader.unbind();
    }

    @Override
    public void swapBuffers(float frameTime) {
        glfwSwapBuffers(windowId);
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }

    @Override
    public void setBlend(boolean enable, BlendFactor src, BlendFactor dst) {
        if (enable) {
            if (!blendEnabled) {
                glEnable(GL_BLEND);
                glBlendEquation(GL_FUNC_ADD);
                blendEnabled = true;
            }
            if (src != blendSrc || dst != blendDst) {
                glBlendFunc(toGL(src), toGL(dst));
                blendSrc = src;
                blendDst = dst;
            }
        } else {
            if (blendEnabled) {
                glDisable(GL_BLEND);
                blendEnabled = false;
            }
        }
    }

    @Override
    public void setScissor(boolean enable, int x, int y, int width, int height) {
        if (enable) {
            glEnable(GL_SCISSOR_TEST);
            glScissor(x, y, width, height);
        } else {
            glDisable(GL_SCISSOR_TEST);
        }
    }

    @Override
    public void renderRaw(
            VertexArray vao,
            Shader shader,
            RenderMode renderMode,
            PolygonMode polygonMode,
            int elementOffset,
            int elementCount) {
        shader.bind();
        vao.bind();

        if (prevPolygonMode != polygonMode) {
            glPolygonMode(GL41.GL_FRONT_AND_BACK, polygonMode.glRef);
            prevPolygonMode = polygonMode;
        }

        if (vao.getEBO().isPresent()) {
            var ebo = vao.getEBO().get();
            var glIndexType = ebo instanceof OpenGLElementBuffer oebo && oebo.getIndexType() == IndexType.SHORT
                    ? GL_UNSIGNED_SHORT
                    : GL_UNSIGNED_INT;
            glDrawElements(renderMode.glRef, elementCount, glIndexType, elementOffset);
        }

        vao.unbind();
        shader.unbind();
    }

    @Override
    public void windowResized(int newWidth, int newHeight) {
        windowWidth = newWidth;
        windowHeight = newHeight;
        glViewport(0, 0, newWidth, newHeight);
    }

    @Override
    public void setRenderTarget(RenderTarget renderTarget) {
        if (renderTarget instanceof OpenGLRenderTarget rt) {
            glBindFramebuffer(GL_FRAMEBUFFER, rt.fboId());
            glViewport(0, 0, rt.getWidth(), rt.getHeight());
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, windowWidth, windowHeight);
        }
    }

    @Override
    public void dispose() {}

    @Override
    public String getRendererInfo() {
        return rendererInfo;
    }

    private static int vertexCount(hu.mudlee.core.render.VertexBuffer vbo) {
        var strideBytes = vbo.getLayout().attributes()[0].getStride();
        return (vbo.getLength() * Float.BYTES) / strideBytes;
    }

    private static int toGL(BlendFactor factor) {
        return switch (factor) {
            case ZERO -> GL_ZERO;
            case ONE -> GL_ONE;
            case SRC_ALPHA -> GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
        };
    }
}
