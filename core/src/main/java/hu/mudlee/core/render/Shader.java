package hu.mudlee.core.render;

import hu.mudlee.core.render.opengl.OpenGLShader;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public abstract class Shader {
	public static Shader create(String vertexShaderName, String fragmentShaderName) {
		return new OpenGLShader(vertexShaderName, fragmentShaderName);
	}

	public abstract int getPipelineId();

	public abstract void bind();

	public abstract void unbind();

	public abstract int getVertexProgramId();

	public abstract int getFragmentProgramId();

	public abstract void createUniform(int programId, String name);

	public abstract void setUniform(int programId, String name, Matrix4f value);

	public abstract void setUniform(int programId, String name, Vector4f value);

	public abstract void setUniform(int programId, String name, float value);

	public abstract void setUniform(int programId, String name, int value);

	public abstract void dispose();
}
