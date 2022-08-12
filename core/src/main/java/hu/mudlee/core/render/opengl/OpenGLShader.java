package hu.mudlee.core.render.opengl;

import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class OpenGLShader extends Shader {
	private static final Logger log = LoggerFactory.getLogger(OpenGLShader.class);
	private final int pipelineId;
	private final Map<String, Integer> uniforms = new HashMap<>();
	private final int vertexId;
	private final int fragmentId;

	public OpenGLShader(String vertexShaderName, String fragmentShaderName) {
		log.debug("Creating shader pipeline program for vertex shader '{}' and fragment shader '{}'", vertexShaderName, fragmentShaderName);
		pipelineId = glGenProgramPipelines();
		log.debug(" * Pipeline was created ID:{} for {}", pipelineId, this.getClass().getSimpleName());

		final var vertPath = String.format("/shaders/%s", vertexShaderName);
		final var fragPath = String.format("/shaders/%s", fragmentShaderName);

		vertexId = glCreateShaderProgramv(GL_VERTEX_SHADER, ResourceLoader.load(vertPath));
		validateShader(vertexId, vertPath);
		glUseProgramStages(pipelineId, GL_VERTEX_SHADER_BIT, vertexId);
		log.debug(" * Vertex shader ready ID:{}", vertexId);

		fragmentId = glCreateShaderProgramv(GL_FRAGMENT_SHADER, ResourceLoader.load(fragPath));
		validateShader(fragmentId, fragPath);
		glUseProgramStages(pipelineId, GL_FRAGMENT_SHADER_BIT, fragmentId);
		log.debug(" * Fragment shader ready ID:{}", fragmentId);
	}

	@Override
	public int getPipelineId() {
		return pipelineId;
	}

	@Override
	public void bind() {
		log.trace("Bind shader pipeline {}", pipelineId);
		glBindProgramPipeline(pipelineId);
	}

	@Override
	public void unbind() {
		log.trace("Unbind shader pipeline {}", pipelineId);
		glBindProgramPipeline(0);
	}

	@Override
	public int getVertexProgramId() {
		return vertexId;
	}

	@Override
	public int getFragmentProgramId() {
		return fragmentId;
	}

	@Override
	public void createUniform(int programId, String name) {
		if (uniforms.containsKey(name)) {
			log.error("Uniform [NAME:{}] is already created with ID {}", name, uniforms.get(name));
			return;
		}

		int location = glGetUniformLocation(programId, name);
		if (location < 0) {
			log.error("Uniform could not find ({}) or not used, so optimized out when trying to create: {}", location, name);
		} else {
			log.debug("Uniform [NAME:{}] [LOCATION:{}] created for shader [ID:{}] in pipeline [ID:{}]", name, location, programId, this.pipelineId);
			uniforms.put(name, location);
		}
	}

	@Override
	public void setUniform(int programId, String name, Matrix4f value) {
		if(doesUniformExist(name)) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				final var buffer = stack.mallocFloat(16);
				value.get(buffer);
				glProgramUniformMatrix4fv(programId, uniforms.get(name), false, buffer);
			}
		}
	}

	@Override
	public void dispose() {
		log.trace("Dispose shader pipeline {}", pipelineId);
		unbind();

		glDeleteProgramPipelines(pipelineId);
	}

	private boolean doesUniformExist(String uniformName) {
		if (!uniforms.containsKey(uniformName)) {
			log.error("Uniform '{}' was not created, cannot set", uniformName);
			return false;
		}

		return true;
	}

	private void validateShader(int shaderId, String path) {
		validate(shaderId, path, GL_LINK_STATUS, "GL_LINK_STATUS");
		validate(shaderId, path, GL_VALIDATE_STATUS, "GL_VALIDATE_STATUS");
	}

	private void validate(int shaderId, String path, int pname, String pnameReadable) {
		try (MemoryStack stack = stackPush()) {
			final var buffer = stack.callocInt(1);
			glGetProgramiv(shaderId, pname, buffer);

			if (buffer.get() == GL_FALSE) {
				log.error(
						" * Validation {} failed for '{}'\n---\n{}---",
						pnameReadable,
						path,
						glGetProgramInfoLog(shaderId, 1024)
				);
				throw new RuntimeException("Shader validating failed");
			}

			log.debug(" * Validation {} OK", pnameReadable);
		}
	}
}
