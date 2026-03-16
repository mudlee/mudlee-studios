package hu.mudlee.core.render;

import hu.mudlee.core.render.vulkan.VulkanShader;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public abstract class Shader {
    public static Shader create(String vertexShaderName, String fragmentShaderName) {
        return new VulkanShader(vertexShaderName, fragmentShaderName);
    }

    public abstract int getPipelineId();

    public abstract void bind();

    public abstract void unbind();

    public abstract void createUniform(String name);

    public abstract void setUniform(String name, Matrix4f value);

    public abstract void setUniform(String name, Vector4f value);

    public abstract void setUniform(String name, float value);

    public abstract void setUniform(String name, int value);

    public abstract void dispose();
}
