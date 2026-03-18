package hu.mudlee.core.render;

import hu.mudlee.core.render.texture.Texture2D;
import java.nio.ByteBuffer;

public interface RenderBackendFactory {
    GraphicsContext createGraphicsContext(boolean debug);

    VertexArray createVertexArray();

    VertexBuffer createVertexBuffer(float[] vertices, VertexBufferLayout layout);

    VertexBuffer createDynamicVertexBuffer(VertexBufferLayout layout, int maxFloats);

    ElementBuffer createElementBuffer(int[] indices);

    Shader createShader(String vertexShaderName, String fragmentShaderName);

    Texture2D createTexture(String path);

    Texture2D createTextureFromPixels(ByteBuffer pixels, int width, int height, boolean pixelPerfect);

    RenderTarget createRenderTarget(int width, int height);
}
