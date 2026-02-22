package hu.mudlee.core.render;

import hu.mudlee.core.Color;
import hu.mudlee.core.Disposable;
import hu.mudlee.core.Rectangle;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import hu.mudlee.core.render.types.ShaderProps;
import hu.mudlee.core.render.types.ShaderTypes;
import hu.mudlee.core.window.Window;
import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * Batches 2D sprite draw calls into a single GPU draw per texture.
 *
 * <p>Usage:
 *
 * <pre>
 * spriteBatch.begin(camera.getProjectionMatrix(), camera.getViewMatrix());
 * spriteBatch.draw(texture, position, Color.WHITE);
 * spriteBatch.end();
 * </pre>
 *
 * <p>Internally uses 6 non-indexed vertices per sprite (two triangles). The dynamic VBO is updated
 * on {@link #end()} (or when the batch fills up or the texture changes).
 */
public class SpriteBatch implements Disposable {

    private static final int MAX_SPRITES = 1000;
    private static final int FLOATS_PER_VERTEX = 9; // vec3 pos + vec4 color + vec2 uv
    private static final int VERTICES_PER_SPRITE = 6; // two non-indexed triangles
    private static final int FLOATS_PER_SPRITE = VERTICES_PER_SPRITE * FLOATS_PER_VERTEX;
    private static final int MAX_FLOATS = MAX_SPRITES * FLOATS_PER_SPRITE;

    private final Shader shader;
    private final VertexArray vertexArray;
    private final VertexBuffer dynamicVbo;
    private final Matrix4f identityMatrix = new Matrix4f();

    private final float[] vertexData = new float[MAX_FLOATS];
    private int spriteCount;
    private boolean begun;
    private Texture2D currentTexture;

    public SpriteBatch() {
        var stride = FLOATS_PER_VERTEX * Float.BYTES;
        var layout = new VertexBufferLayout(
                new VertexLayoutAttribute(0, 3, ShaderTypes.FLOAT, false, stride, 0),
                new VertexLayoutAttribute(1, 4, ShaderTypes.FLOAT, false, stride, 3 * Float.BYTES),
                new VertexLayoutAttribute(2, 2, ShaderTypes.FLOAT, false, stride, 7 * Float.BYTES));

        dynamicVbo = VertexBuffer.createDynamic(layout, MAX_FLOATS);
        vertexArray = VertexArray.create();
        vertexArray.addVBO(dynamicVbo);

        shader = Shader.create("vulkan/2d/vert.glsl", "vulkan/2d/frag.glsl");
        shader.createUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName);
        shader.createUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName);
        shader.createUniform(shader.getFragmentProgramId(), "TEX_SAMPLER");
        shader.setUniform(shader.getFragmentProgramId(), "TEX_SAMPLER", 0);
        shader.setUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName, identityMatrix);
    }

    public void begin() {
        var size = Window.getSize();
        var ortho = new Matrix4f().setOrtho(0f, size.x, 0f, size.y, -1f, 1f);
        begin(ortho, identityMatrix);
    }

    public void begin(Matrix4f transformMatrix) {
        begin(transformMatrix, identityMatrix);
    }

    public void begin(Matrix4f projection, Matrix4f view) {
        if (begun) {
            throw new IllegalStateException("SpriteBatch.begin() called without a matching end()");
        }
        begun = true;
        spriteCount = 0;
        currentTexture = null;
        shader.setUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName, projection);
        shader.setUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName, view);
    }

    public void draw(Texture2D texture, Vector2f position, Color color) {
        draw(texture, position.x, position.y, texture.getWidth(), texture.getHeight(), color);
    }

    public void draw(Texture2D texture, Rectangle destinationRect, Color color) {
        draw(texture, destinationRect.x, destinationRect.y, destinationRect.width, destinationRect.height, color);
    }

    public void draw(
            Texture2D texture,
            Vector2f position,
            Color color,
            float rotation,
            Vector2f origin,
            float scale,
            boolean flipX,
            boolean flipY) {
        var w = texture.getWidth() * scale;
        var h = texture.getHeight() * scale;
        draw(texture, position.x, position.y, w, h, color);
    }

    public void end() {
        if (!begun) {
            throw new IllegalStateException("SpriteBatch.end() called without a matching begin()");
        }
        flush();
        begun = false;
    }

    @Override
    public void dispose() {
        shader.dispose();
        vertexArray.dispose();
    }

    private void draw(Texture2D texture, float x, float y, float w, float h, Color color) {
        if (!begun) {
            throw new IllegalStateException("SpriteBatch.draw() called outside begin()/end()");
        }
        if (spriteCount >= MAX_SPRITES || (currentTexture != null && currentTexture != texture)) {
            flush();
        }
        if (currentTexture == null) {
            currentTexture = texture;
        }
        writeQuad(x, y, w, h, color);
        spriteCount++;
    }

    private void flush() {
        if (spriteCount == 0) {
            return;
        }
        var floatCount = spriteCount * FLOATS_PER_SPRITE;
        dynamicVbo.update(vertexData, floatCount);
        currentTexture.bind();
        Renderer.renderRaw(vertexArray, shader, RenderMode.TRIANGLES, PolygonMode.FILL);
        spriteCount = 0;
        currentTexture = null;
    }

    private void writeQuad(float x, float y, float w, float h, Color color) {
        var base = spriteCount * FLOATS_PER_SPRITE;
        var r = color.r;
        var g = color.g;
        var b = color.b;
        var a = color.a;

        // Triangle 1: BL, BR, TR
        writeVertex(base, x, y, r, g, b, a, 0f, 1f);
        writeVertex(base + FLOATS_PER_VERTEX, x + w, y, r, g, b, a, 1f, 1f);
        writeVertex(base + FLOATS_PER_VERTEX * 2, x + w, y + h, r, g, b, a, 1f, 0f);

        // Triangle 2: BL, TR, TL
        writeVertex(base + FLOATS_PER_VERTEX * 3, x, y, r, g, b, a, 0f, 1f);
        writeVertex(base + FLOATS_PER_VERTEX * 4, x + w, y + h, r, g, b, a, 1f, 0f);
        writeVertex(base + FLOATS_PER_VERTEX * 5, x, y + h, r, g, b, a, 0f, 0f);
    }

    private void writeVertex(int offset, float x, float y, float r, float g, float b, float a, float u, float v) {
        vertexData[offset] = x;
        vertexData[offset + 1] = y;
        vertexData[offset + 2] = 0f;
        vertexData[offset + 3] = r;
        vertexData[offset + 4] = g;
        vertexData[offset + 5] = b;
        vertexData[offset + 6] = a;
        vertexData[offset + 7] = u;
        vertexData[offset + 8] = v;
    }
}
