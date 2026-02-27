package hu.mudlee.core.render;

import hu.mudlee.core.Color;
import hu.mudlee.core.Disposable;
import hu.mudlee.core.Rectangle;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.texture.TextureRegion;
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
public class SpriteBatch2D implements Disposable {

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

    public SpriteBatch2D() {
        var stride = FLOATS_PER_VERTEX * Float.BYTES;
        var layout = new VertexBufferLayout(
                new VertexLayoutAttribute(0, 3, ShaderTypes.FLOAT, false, stride, 0),
                new VertexLayoutAttribute(1, 4, ShaderTypes.FLOAT, false, stride, 3 * Float.BYTES),
                new VertexLayoutAttribute(2, 2, ShaderTypes.FLOAT, false, stride, 7 * Float.BYTES));

        dynamicVbo = VertexBuffer.createDynamic(layout, MAX_FLOATS);
        vertexArray = VertexArray.create();
        vertexArray.addVBO(dynamicVbo);

        var shaderDir =
                switch (Renderer.activeBackend()) {
                    case OPENGL -> "opengl/2d";
                    case VULKAN -> "vulkan/2d";
                };
        shader = Shader.create(shaderDir + "/vert.glsl", shaderDir + "/frag.glsl");
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
            throw new IllegalStateException("SpriteBatch2D.begin() called without a matching end()");
        }
        begun = true;
        spriteCount = 0;
        currentTexture = null;
        shader.setUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName, projection);
        shader.setUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName, view);
    }

    public void draw(Texture2D texture, Vector2f position, Color color) {
        draw(texture, position.x, position.y, texture.getWidth(), texture.getHeight(), color, 0f, 0f, 1f, 1f);
    }

    public void draw(Texture2D texture, Rectangle destinationRect, Color color) {
        draw(
                texture,
                destinationRect.x,
                destinationRect.y,
                destinationRect.width,
                destinationRect.height,
                color,
                0f,
                0f,
                1f,
                1f);
    }

    public void draw(Texture2D texture, Vector2f position, Rectangle sourceRect, Color color) {
        var tw = texture.getWidth();
        var th = texture.getHeight();
        var u0 = (float) sourceRect.x / tw;
        var v0 = (float) sourceRect.y / th;
        var u1 = (float) (sourceRect.x + sourceRect.width) / tw;
        var v1 = (float) (sourceRect.y + sourceRect.height) / th;
        draw(texture, position.x, position.y, sourceRect.width, sourceRect.height, color, u0, v0, u1, v1);
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
        var u0 = flipX ? 1f : 0f;
        var u1 = flipX ? 0f : 1f;
        var v0 = flipY ? 1f : 0f;
        var v1 = flipY ? 0f : 1f;
        draw(texture, position.x, position.y, w, h, color, u0, v0, u1, v1, rotation, origin.x, origin.y);
    }

    public void draw(TextureRegion region, Vector2f position, Color color) {
        draw(
                region.texture,
                position.x,
                position.y,
                region.width,
                region.height,
                color,
                region.u0(),
                region.v0(),
                region.u1(),
                region.v1());
    }

    public void draw(
            TextureRegion region,
            Vector2f position,
            Color color,
            float rotation,
            Vector2f origin,
            float scale,
            boolean flipX,
            boolean flipY) {
        var w = region.width * scale;
        var h = region.height * scale;
        var u0 = flipX ? region.u1() : region.u0();
        var u1 = flipX ? region.u0() : region.u1();
        var v0 = flipY ? region.v1() : region.v0();
        var v1 = flipY ? region.v0() : region.v1();
        draw(region.texture, position.x, position.y, w, h, color, u0, v0, u1, v1, rotation, origin.x, origin.y);
    }

    public void end() {
        if (!begun) {
            throw new IllegalStateException("SpriteBatch2D.end() called without a matching begin()");
        }
        flush();
        begun = false;
    }

    @Override
    public void dispose() {
        shader.dispose();
        vertexArray.dispose();
    }

    private void draw(
            Texture2D texture,
            float x,
            float y,
            float w,
            float h,
            Color color,
            float u0,
            float v0,
            float u1,
            float v1) {
        if (!begun) {
            throw new IllegalStateException("SpriteBatch2D.draw() called outside begin()/end()");
        }
        if (spriteCount >= MAX_SPRITES || (currentTexture != null && currentTexture != texture)) {
            flush();
        }
        if (currentTexture == null) {
            currentTexture = texture;
        }
        writeQuad(x, y, w, h, color, u0, v0, u1, v1);
        spriteCount++;
    }

    private void draw(
            Texture2D texture,
            float x,
            float y,
            float w,
            float h,
            Color color,
            float u0,
            float v0,
            float u1,
            float v1,
            float rotation,
            float ox,
            float oy) {
        if (!begun) {
            throw new IllegalStateException("SpriteBatch2D.draw() called outside begin()/end()");
        }
        if (spriteCount >= MAX_SPRITES || (currentTexture != null && currentTexture != texture)) {
            flush();
        }
        if (currentTexture == null) {
            currentTexture = texture;
        }
        writeQuadRotated(x, y, w, h, color, u0, v0, u1, v1, rotation, ox, oy);
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

    private void writeQuad(float x, float y, float w, float h, Color color, float u0, float v0, float u1, float v1) {
        var base = spriteCount * FLOATS_PER_SPRITE;
        var r = color.r;
        var g = color.g;
        var b = color.b;
        var a = color.a;

        // Triangle 1: BL, BR, TR
        writeVertex(base, x, y, r, g, b, a, u0, v1);
        writeVertex(base + FLOATS_PER_VERTEX, x + w, y, r, g, b, a, u1, v1);
        writeVertex(base + FLOATS_PER_VERTEX * 2, x + w, y + h, r, g, b, a, u1, v0);

        // Triangle 2: BL, TR, TL
        writeVertex(base + FLOATS_PER_VERTEX * 3, x, y, r, g, b, a, u0, v1);
        writeVertex(base + FLOATS_PER_VERTEX * 4, x + w, y + h, r, g, b, a, u1, v0);
        writeVertex(base + FLOATS_PER_VERTEX * 5, x, y + h, r, g, b, a, u0, v0);
    }

    private void writeQuadRotated(
            float x,
            float y,
            float w,
            float h,
            Color color,
            float u0,
            float v0,
            float u1,
            float v1,
            float rotation,
            float ox,
            float oy) {
        var base = spriteCount * FLOATS_PER_SPRITE;
        var r = color.r;
        var g = color.g;
        var b = color.b;
        var a = color.a;
        var cos = (float) Math.cos(rotation);
        var sin = (float) Math.sin(rotation);
        // Pivot in world space
        var px = x + ox;
        var py = y + oy;
        // BL corner: offset (-ox, -oy) from pivot
        var blX = px + (-ox) * cos - (-oy) * sin;
        var blY = py + (-ox) * sin + (-oy) * cos;
        // BR corner: offset (w-ox, -oy) from pivot
        var brX = px + (w - ox) * cos - (-oy) * sin;
        var brY = py + (w - ox) * sin + (-oy) * cos;
        // TR corner: offset (w-ox, h-oy) from pivot
        var trX = px + (w - ox) * cos - (h - oy) * sin;
        var trY = py + (w - ox) * sin + (h - oy) * cos;
        // TL corner: offset (-ox, h-oy) from pivot
        var tlX = px + (-ox) * cos - (h - oy) * sin;
        var tlY = py + (-ox) * sin + (h - oy) * cos;

        // Triangle 1: BL, BR, TR
        writeVertex(base, blX, blY, r, g, b, a, u0, v1);
        writeVertex(base + FLOATS_PER_VERTEX, brX, brY, r, g, b, a, u1, v1);
        writeVertex(base + FLOATS_PER_VERTEX * 2, trX, trY, r, g, b, a, u1, v0);
        // Triangle 2: BL, TR, TL
        writeVertex(base + FLOATS_PER_VERTEX * 3, blX, blY, r, g, b, a, u0, v1);
        writeVertex(base + FLOATS_PER_VERTEX * 4, trX, trY, r, g, b, a, u1, v0);
        writeVertex(base + FLOATS_PER_VERTEX * 5, tlX, tlY, r, g, b, a, u0, v0);
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
