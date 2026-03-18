package hu.mudlee.core.render;

import org.joml.Matrix4f;

public abstract class Shader {
    public static Shader create(String vertexShaderName, String fragmentShaderName) {
        return Renderer.backendFactory().createShader(vertexShaderName, fragmentShaderName);
    }

    public abstract void setUniform(String name, Matrix4f value);

    public abstract void dispose();
}
