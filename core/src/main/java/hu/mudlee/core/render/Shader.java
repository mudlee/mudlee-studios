package hu.mudlee.core.render;

import hu.mudlee.core.render.types.ShaderConfig;
import hu.mudlee.core.render.vulkan.VulkanRenderBackendFactory;
import org.joml.Matrix4f;

public abstract class Shader {
    public static Shader create(String vertexShaderName, String fragmentShaderName) {
        return create(
                vertexShaderName,
                fragmentShaderName,
                ShaderConfig.inferFromPaths(vertexShaderName, fragmentShaderName));
    }

    public static Shader create(String vertexShaderName, String fragmentShaderName, ShaderConfig config) {
        var factory = Renderer.backendFactory();
        if (factory instanceof VulkanRenderBackendFactory vulkanFactory) {
            return vulkanFactory.createShader(vertexShaderName, fragmentShaderName, config);
        }
        return factory.createShader(vertexShaderName, fragmentShaderName);
    }

    public abstract void setUniform(String name, Matrix4f value);

    public abstract void dispose();
}
