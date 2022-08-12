package hu.mudlee.core.render.opengl;

import hu.mudlee.core.render.GraphicsContext;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.types.PolygonMode;
import org.joml.Vector4f;
import org.lwjgl.opengl.GLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL41.*;
import org.lwjgl.opengl.GL41;

public class OpenGLGraphicsContext implements GraphicsContext {
	private static final Logger log = LoggerFactory.getLogger(OpenGLGraphicsContext.class);
	private final boolean debug;
	private int clearFlags = 0;
	private long windowId;
	private int prevPolygonMode = PolygonMode.FILL;

	public OpenGLGraphicsContext(boolean debug) {
		this.debug = debug;
	}

	@Override
	public void windowPrepared() {
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
		if(debug){
			glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
		}
	}

	@Override
	public void windowCreated(long windowId, int windowWidth, int windowHeight, boolean vSync) {
		log.debug("Initializing OpenGL context...");
		this.windowId = windowId;

		glfwMakeContextCurrent(this.windowId);

		createCapabilities();

		if (debug) {
			GLUtil.setupDebugMessageCallback();
		}

		log.debug("Initialized");
		log.debug("\tOpenGL Vendor: {}", glGetString(GL_VENDOR));
		log.debug("\tVersion: {}", glGetString(GL_VERSION));
		log.debug("\tRenderer: {}", glGetString(GL_RENDERER));
		log.debug("\tShading Language Version: {}", glGetString(GL_SHADING_LANGUAGE_VERSION));
		log.debug("\tVsync: {}", vSync);

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

	@Override
	public void renderRaw(VertexArray vao, Shader shader, int renderMode, int polygonMode) {
		shader.bind();
		vao.bind();

		if(prevPolygonMode != polygonMode) {
			glPolygonMode(GL41.GL_FRONT_AND_BACK, polygonMode);
		}

		if(vao.isInstanced()) {
			if (vao.getIndexBuffer().isPresent()) {
				glDrawElementsInstanced(renderMode, vao.getIndexBuffer().get().getLength(), GL_UNSIGNED_INT, 0, vao.getInstanceCount());
			}
			else {
				for (VertexBuffer buffer : vao.getVertexBuffers()) {
					// NOTE: we suppose that vertex coordinates always passed as vec3
					glDrawArraysInstanced(renderMode, 0, buffer.getLength() / 3, vao.getInstanceCount());
				}
			}
		}
		else {
			if (vao.getIndexBuffer().isPresent()) {
				glDrawElements(renderMode, vao.getIndexBuffer().get().getLength(), GL_UNSIGNED_INT, 0);
			} else {
				for (VertexBuffer buffer : vao.getVertexBuffers()) {
					// NOTE: we suppose that vertex coordinates always passed as vec3
					glDrawArrays(renderMode, 0, buffer.getLength() / 3);
				}
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
	public void windowResized(int newWidth, int newHeight) {
		glViewport(0, 0, newWidth, newHeight);
	}

	@Override
	public void dispose() {
	}
}
