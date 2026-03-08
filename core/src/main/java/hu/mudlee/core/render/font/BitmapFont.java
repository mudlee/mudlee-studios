package hu.mudlee.core.render.font;

import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.texture.Texture2D;
import org.lwjgl.stb.STBTTBakedChar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rasterises a TrueType font at a fixed point size into a GPU texture atlas.
 *
 * <p>Usage:
 *
 * <pre>
 * var font = new BitmapFont("fonts/Inter.ttf", 18);
 * // in draw():
 * uiBatch.drawText(font, "Hello", 10, 10, Color.WHITE);
 * // on shutdown:
 * font.dispose();
 * </pre>
 */
public final class BitmapFont implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(BitmapFont.class);

    private static final int ATLAS_SIZE = 512;
    private static final int FIRST_CHAR = 32;
    private static final int CHAR_COUNT = 96; // ASCII 32–127

    private final STBTTBakedChar.Buffer charData = STBTTBakedChar.malloc(CHAR_COUNT);
    private final Texture2D atlasTexture;
    private final float ascent;
    private final float ptSize;

    public BitmapFont(String ttfResourcePath, float ptSize) {
        this.ptSize = ptSize;

        var ttfBytes = ResourceLoader.loadToDirectByteBuffer(ttfResourcePath);

        var bitmap = memAlloc(ATLAS_SIZE * ATLAS_SIZE);
        stbtt_BakeFontBitmap(ttfBytes, ptSize, bitmap, ATLAS_SIZE, ATLAS_SIZE, FIRST_CHAR, charData);
        memFree(ttfBytes);

        // Convert single-channel alpha bitmap to RGBA for Texture2D.createFromPixels
        var rgba = memAlloc(ATLAS_SIZE * ATLAS_SIZE * 4);
        for (int i = 0; i < ATLAS_SIZE * ATLAS_SIZE; i++) {
            byte a = bitmap.get(i);
            rgba.put((byte) 0xFF); // R
            rgba.put((byte) 0xFF); // G
            rgba.put((byte) 0xFF); // B
            rgba.put(a); // A
        }
        rgba.flip();
        memFree(bitmap);

        atlasTexture = Texture2D.createFromPixels(rgba, ATLAS_SIZE, ATLAS_SIZE);
        memFree(rgba);

        try (var stack = stackPush()) {
            var ascentBuf = stack.mallocInt(1);
            var descentBuf = stack.mallocInt(1);
            var lineGapBuf = stack.mallocInt(1);
            // Re-load font info just to get ascent — small cost, only at construction
            var info = org.lwjgl.stb.STBTTFontinfo.malloc(stack);
            var reloaded = ResourceLoader.loadToDirectByteBuffer(ttfResourcePath);
            stbtt_InitFont(info, reloaded);
            stbtt_GetFontVMetrics(info, ascentBuf, descentBuf, lineGapBuf);
            float scale = stbtt_ScaleForPixelHeight(info, ptSize);
            ascent = ascentBuf.get(0) * scale;
            memFree(reloaded);
        }

        log.debug("BitmapFont '{}' @ {}pt baked onto {}×{} atlas", ttfResourcePath, ptSize, ATLAS_SIZE, ATLAS_SIZE);
    }

    public Texture2D getAtlasTexture() {
        return atlasTexture;
    }

    /** The ascent (baseline offset from top of line) in pixels for this font size. */
    public float getAscent() {
        return ascent;
    }

    public float getPtSize() {
        return ptSize;
    }

    /**
     * Fills {@code quad} with the screen-space position and UV coordinates for the given character.
     * Advances {@code xCursor} by the character's advance width.
     *
     * @param c        the character to look up (must be in ASCII 32–127 range)
     * @param xCursor  x-position cursor (updated in place)
     * @param yCursor  y-position cursor (baseline, not top)
     * @param quad     output quad — caller must allocate via {@link org.lwjgl.stb.STBTTAlignedQuad#malloc}
     */
    public void getQuad(char c, float[] xCursor, float[] yCursor, org.lwjgl.stb.STBTTAlignedQuad quad) {
        int idx = c - FIRST_CHAR;
        if (idx < 0 || idx >= CHAR_COUNT) {
            return;
        }
        try (var stack = stackPush()) {
            var xb = stack.floats(xCursor[0]);
            var yb = stack.floats(yCursor[0]);
            stbtt_GetBakedQuad(charData, ATLAS_SIZE, ATLAS_SIZE, idx, xb, yb, quad, true);
            xCursor[0] = xb.get(0);
            yCursor[0] = yb.get(0);
        }
    }

    /**
     * Returns the pixel width of the given string at this font's size.
     * Useful for aligning columns.
     */
    public float measureTextWidth(String text) {
        try (var quad = org.lwjgl.stb.STBTTAlignedQuad.malloc()) {
            var xCursor = new float[] {0f};
            var yCursor = new float[] {0f};
            for (int i = 0; i < text.length(); i++) {
                getQuad(text.charAt(i), xCursor, yCursor, quad);
            }
            return xCursor[0];
        }
    }

    @Override
    public void dispose() {
        charData.free();
        atlasTexture.dispose();
    }
}
