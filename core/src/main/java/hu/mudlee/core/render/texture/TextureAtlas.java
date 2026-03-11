package hu.mudlee.core.render.texture;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.io.ResourceLoader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBRPContext;
import org.lwjgl.stb.STBRPNode;
import org.lwjgl.stb.STBRPRect;
import org.lwjgl.stb.STBRectPack;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packs multiple source images into a single GPU texture atlas.
 *
 * <p>All sprites from the same atlas share one {@link Texture2D}, so {@link
 * hu.mudlee.core.render.SpriteBatch2D} never needs to flush mid-batch due to a texture switch.
 *
 * <pre>
 * var atlas = new TextureAtlas.Builder()
 *         .add("player", "/textures/player.png")
 *         .add("enemy",  "/textures/enemy.png")
 *         .build();
 *
 * batch.draw(atlas.getRegion("player"), position, Color.WHITE);
 * </pre>
 */
public final class TextureAtlas implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(TextureAtlas.class);

    private final Texture2D texture;
    private final Map<String, TextureRegion> regions;

    private TextureAtlas(Texture2D texture, Map<String, TextureRegion> regions) {
        this.texture = texture;
        this.regions = regions;
    }

    /** Returns the named region, or throws if the name was not added to the builder. */
    public TextureRegion getRegion(String name) {
        var region = regions.get(name);
        if (region == null) {
            throw new IllegalArgumentException("No region '" + name + "' in atlas");
        }
        return region;
    }

    public Texture2D getTexture() {
        return texture;
    }

    @Override
    public void dispose() {
        texture.dispose();
    }

    public static final class Builder {

        private final List<String> names = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();

        public Builder add(String name, String resourcePath) {
            names.add(name);
            paths.add(resourcePath);
            return this;
        }

        public TextureAtlas build() {
            var count = names.size();
            if (count == 0) {
                throw new IllegalStateException("TextureAtlas.Builder has no images");
            }

            var imageData = loadImages(count);
            var rects = packRects(imageData, count);
            var atlasTexture = compositeAndUpload(imageData, rects, count);
            var regionMap = buildRegionMap(atlasTexture, imageData, rects, count);
            rects.free();
            return new TextureAtlas(atlasTexture, regionMap);
        }

        private TextureData[] loadImages(int count) {
            var imageData = new TextureData[count];
            for (var i = 0; i < count; i++) {
                var raw = ResourceLoader.loadToDirectByteBuffer(paths.get(i));
                try (var stack = MemoryStack.stackPush()) {
                    var w = stack.mallocInt(1);
                    var h = stack.mallocInt(1);
                    var channels = stack.mallocInt(1);
                    var pixels = STBImage.stbi_load_from_memory(raw, w, h, channels, 4);
                    if (pixels == null) {
                        throw new RuntimeException("Failed to load atlas image: " + paths.get(i));
                    }
                    imageData[i] = new TextureData(w.get(0), h.get(0), pixels, 4);
                } finally {
                    MemoryUtil.memFree(raw);
                }
            }
            return imageData;
        }

        private static STBRPRect.Buffer packRects(TextureData[] imageData, int count) {
            var totalArea = 0;
            for (var data : imageData) {
                totalArea += data.width() * data.height();
            }
            var side = nextPowerOfTwo((int) Math.ceil(Math.sqrt(totalArea)));

            while (true) {
                var rects = STBRPRect.malloc(count);
                for (var i = 0; i < count; i++) {
                    rects.get(i).id(i).w(imageData[i].width()).h(imageData[i].height());
                }
                var nodes = STBRPNode.malloc(side);
                try (var stack = MemoryStack.stackPush()) {
                    var ctx = STBRPContext.malloc(stack);
                    STBRectPack.stbrp_init_target(ctx, side, side, nodes);
                    STBRectPack.stbrp_pack_rects(ctx, rects);
                } finally {
                    nodes.free();
                }
                var allPacked = true;
                for (var i = 0; i < count; i++) {
                    if (!rects.get(i).was_packed()) {
                        allPacked = false;
                        break;
                    }
                }
                if (allPacked) {
                    return rects;
                }
                rects.free();
                side *= 2;
                LOG.debug("Atlas too small, doubling to {}x{}", side, side);
            }
        }

        private static Texture2D compositeAndUpload(TextureData[] imageData, STBRPRect.Buffer rects, int count) {
            var side = computeAtlasSide(rects, count);
            var atlasPixels = MemoryUtil.memCalloc(side * side * 4);
            try {
                for (var i = 0; i < count; i++) {
                    var rect = rects.get(i);
                    var data = imageData[rect.id()];
                    copyPixels(data.image(), data.width(), data.height(), atlasPixels, side, rect.x(), rect.y());
                    STBImage.stbi_image_free(data.image());
                }
                return Texture2D.createFromPixels(atlasPixels, side, side, true);
            } finally {
                MemoryUtil.memFree(atlasPixels);
            }
        }

        private static int computeAtlasSide(STBRPRect.Buffer rects, int count) {
            var maxEdge = 0;
            for (var i = 0; i < count; i++) {
                var rect = rects.get(i);
                maxEdge = Math.max(maxEdge, rect.x() + rect.w());
                maxEdge = Math.max(maxEdge, rect.y() + rect.h());
            }
            return nextPowerOfTwo(maxEdge);
        }

        private Map<String, TextureRegion> buildRegionMap(
                Texture2D atlasTexture, TextureData[] imageData, STBRPRect.Buffer rects, int count) {
            var regionMap = new HashMap<String, TextureRegion>(count);
            for (var i = 0; i < count; i++) {
                var rect = rects.get(i);
                var id = rect.id();
                regionMap.put(
                        names.get(id),
                        new TextureRegion(
                                atlasTexture, rect.x(), rect.y(), imageData[id].width(), imageData[id].height()));
            }
            return regionMap;
        }

        private static void copyPixels(
                ByteBuffer src, int srcW, int srcH, ByteBuffer dst, int dstStride, int dstX, int dstY) {
            for (var row = 0; row < srcH; row++) {
                for (var col = 0; col < srcW; col++) {
                    var srcIdx = (row * srcW + col) * 4;
                    var dstIdx = ((dstY + row) * dstStride + (dstX + col)) * 4;
                    dst.put(dstIdx, src.get(srcIdx));
                    dst.put(dstIdx + 1, src.get(srcIdx + 1));
                    dst.put(dstIdx + 2, src.get(srcIdx + 2));
                    dst.put(dstIdx + 3, src.get(srcIdx + 3));
                }
            }
        }

        private static int nextPowerOfTwo(int n) {
            if (n <= 1) {
                return 1;
            }
            n--;
            n |= n >> 1;
            n |= n >> 2;
            n |= n >> 4;
            n |= n >> 8;
            n |= n >> 16;
            return n + 1;
        }
    }
}
