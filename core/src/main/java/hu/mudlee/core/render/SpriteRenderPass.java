package hu.mudlee.core.render;

import hu.mudlee.core.Color;
import hu.mudlee.core.render.texture.TextureRegion;
import org.joml.Vector2f;

/**
 * Typed 2D render pass abstraction consumed by sprite-based render systems.
 *
 * <p>This avoids marker-interface dispatch and makes unsupported render paths fail structurally.
 */
public interface SpriteRenderPass {
    void drawSprite(
            TextureRegion region,
            Vector2f position,
            Color color,
            float rotation,
            Vector2f origin,
            float scale,
            boolean flipX,
            boolean flipY);
}
