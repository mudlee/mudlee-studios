package hu.mudlee.core.render.vulkan;

import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.GraphicsContext;
import hu.mudlee.core.render.RenderBackendFactory;
import hu.mudlee.core.render.RenderTarget;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
import hu.mudlee.core.render.texture.Texture2D;
import java.nio.ByteBuffer;

public final class VulkanRenderBackendFactory implements RenderBackendFactory {
    @Override
    public GraphicsContext createGraphicsContext(boolean debug) {
        return new VulkanContext(debug);
    }

    @Override
    public VertexArray createVertexArray() {
        return new VulkanVertexArray();
    }

    @Override
    public VertexBuffer createVertexBuffer(float[] vertices, VertexBufferLayout layout) {
        return new VulkanVertexBuffer(vertices, layout);
    }

    @Override
    public VertexBuffer createDynamicVertexBuffer(VertexBufferLayout layout, int maxFloats) {
        return new VulkanVertexBuffer(layout, maxFloats);
    }

    @Override
    public ElementBuffer createElementBuffer(int[] indices) {
        return new VulkanIndexBuffer(indices);
    }

    @Override
    public Shader createShader(String vertexShaderName, String fragmentShaderName) {
        return new VulkanShader(vertexShaderName, fragmentShaderName);
    }

    @Override
    public Texture2D createTexture(String path) {
        return new VulkanTexture2D(path);
    }

    @Override
    public Texture2D createTextureFromPixels(ByteBuffer pixels, int width, int height, boolean pixelPerfect) {
        return new VulkanTexture2D(pixels, width, height, pixelPerfect);
    }

    @Override
    public RenderTarget createRenderTarget(int width, int height) {
        return new VulkanRenderTarget(width, height);
    }
}
