package hu.mudlee.core.ui;

import hu.mudlee.core.Color;
import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.SpriteBatch2D;
import hu.mudlee.core.render.font.BitmapFont;
import hu.mudlee.core.render.texture.TextureRegion;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTAlignedQuad;

/**
 * Screen-space 2D batch for UI rendering.
 *
 * <p>Wraps {@link SpriteBatch2D} with a pixel-coordinate orthographic projection (origin top-left,
 * y-down) and manages depth test state so UI always renders on top of any 3D scene.
 *
 * <p>Usage:
 *
 * <pre>
 * // in UIService.draw():
 * uiBatch.begin(screenW, screenH);
 * canvas.draw(uiBatch);
 * uiBatch.end();
 * </pre>
 */
public final class UIBatch implements Disposable {

    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 1f);

    private final SpriteBatch2D spriteBatch = new SpriteBatch2D();
    private final Matrix4f ortho = new Matrix4f();
    private final STBTTAlignedQuad quad = STBTTAlignedQuad.malloc();
    private final float[] xCursor = new float[1];
    private final float[] yCursor = new float[1];
    private int screenW, screenH;

    /** Call once after the window is created and whenever the window resizes. */
    public void resize(int width, int height) {
        screenW = width;
        screenH = height;
    }

    /**
     * Begins the UI batch for the current frame. Sets up a screen-space ortho projection (pixel
     * coordinates, origin top-left, y-down).
     *
     * <p>The Vulkan backend already corrects its NDC Y-inversion via a negative-height viewport in
     * {@code VulkanContext.clear()}, so the same OpenGL-style matrix works for both backends.
     */
    public void begin() {
        ortho.setOrtho(0f, screenW, screenH, 0f, -1f, 1f);
        spriteBatch.begin(ortho);
    }

    /** Ends the batch and flushes all queued draw calls to the GPU. */
    public void end() {
        spriteBatch.end();
    }

    /**
     * Draws a string using the given font with a drop shadow (+1, +1) for visibility on any
     * background. {@code x} and {@code y} are the top-left origin in screen pixels.
     */
    public void drawText(BitmapFont font, String text, float x, float y, Color color) {
        drawText(font, text, x, y, color, SHADOW_COLOR);
    }

    /**
     * Draws a string with an explicit shadow colour (used by warning levels that override the default
     * dark shadow, e.g. CRITICAL renders a black outline around red text).
     */
    public void drawText(BitmapFont font, String text, float x, float y, Color color, Color shadowColor) {
        drawTextRaw(font, text, x + 1, y + 1, shadowColor, quad);
        drawTextRaw(font, text, x, y, color, quad);
    }

    private void drawTextRaw(BitmapFont font, String text, float x, float y, Color color, STBTTAlignedQuad quad) {
        xCursor[0] = x;
        yCursor[0] = y + font.getAscent();
        var atlas = font.getAtlasTexture();

        for (int i = 0; i < text.length(); i++) {
            font.getQuad(text.charAt(i), xCursor, yCursor, quad);

            float qx = quad.x0();
            float qy = quad.y0();
            float qw = quad.x1() - quad.x0();
            float qh = quad.y1() - quad.y0();
            float u0 = quad.s0();
            float u1 = quad.s1();
            // SpriteBatch.writeQuad swaps v0/v1 (designed for y-up screens).
            // Pre-swap here so the glyph samples the correct atlas rows.
            float v0 = quad.t1();
            float v1 = quad.t0();

            spriteBatch.draw(atlas, qx, qy, qw, qh, color, u0, v0, u1, v1);
        }
    }

    /** Draws a texture region at screen-pixel coordinates. */
    public void drawSprite(TextureRegion region, float x, float y, float w, float h, Color color) {
        spriteBatch.draw(region.texture, x, y, w, h, color, region.u0(), region.v0(), region.u1(), region.v1());
    }

    @Override
    public void dispose() {
        quad.free();
        spriteBatch.dispose();
    }
}
