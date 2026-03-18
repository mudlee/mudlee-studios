package hu.mudlee.core.render;

import hu.mudlee.core.Color;
import hu.mudlee.core.Disposable;
import hu.mudlee.core.Rectangle;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.texture.TextureRegion;
import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * Small pass coordinator above {@link SpriteBatch2D}.
 *
 * <p>This keeps scene and UI code focused on "what to draw" while the coordinator owns the
 * begin/end lifecycle for the underlying batch.
 */
public final class SpriteRenderCoordinator implements Disposable, SpriteRenderPass {

    private final SpriteBatch2D spriteBatch = new SpriteBatch2D();
    private final Matrix4f identityMatrix = new Matrix4f();
    private final Matrix4f screenSpaceMatrix = new Matrix4f();
    private final RenderTarget renderTarget;
    private final RenderPassOptions passOptions;

    public SpriteRenderCoordinator() {
        this(null, RenderPassOptions.clearColor());
    }

    public SpriteRenderCoordinator(RenderPassOptions passOptions) {
        this(null, passOptions);
    }

    public SpriteRenderCoordinator(RenderTarget renderTarget, RenderPassOptions passOptions) {
        this.renderTarget = renderTarget;
        this.passOptions = passOptions;
    }

    public void begin(Matrix4f transformMatrix) {
        Renderer.beginRenderPass(renderTarget, passOptions);
        spriteBatch.begin(transformMatrix);
    }

    public void begin(Matrix4f projection, Matrix4f view) {
        Renderer.beginRenderPass(renderTarget, passOptions);
        spriteBatch.begin(projection, view);
    }

    public void beginScreenSpace(int width, int height) {
        screenSpaceMatrix.setOrtho(0f, width, height, 0f, -1f, 1f);
        Renderer.beginRenderPass(renderTarget, passOptions);
        spriteBatch.begin(screenSpaceMatrix, identityMatrix);
    }

    public void end() {
        spriteBatch.end();
        Renderer.endRenderPass();
    }

    public void draw(
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
        spriteBatch.draw(texture, x, y, w, h, color, u0, v0, u1, v1);
    }

    public void draw(Texture2D texture, Vector2f position, Color color) {
        spriteBatch.draw(texture, position, color);
    }

    public void draw(Texture2D texture, Rectangle destinationRect, Color color) {
        spriteBatch.draw(texture, destinationRect, color);
    }

    public void draw(TextureRegion region, Vector2f position, Color color) {
        spriteBatch.draw(region, position, color);
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
        spriteBatch.draw(region, position, color, rotation, origin, scale, flipX, flipY);
    }

    @Override
    public void drawSprite(
            TextureRegion region,
            Vector2f position,
            Color color,
            float rotation,
            Vector2f origin,
            float scale,
            boolean flipX,
            boolean flipY) {
        draw(region, position, color, rotation, origin, scale, flipX, flipY);
    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
    }
}
