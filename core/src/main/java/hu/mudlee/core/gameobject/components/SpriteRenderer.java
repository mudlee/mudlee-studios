package hu.mudlee.core.gameobject.components;

import hu.mudlee.core.Color;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.gameobject.Component;
import hu.mudlee.core.render.SpriteBatch;
import hu.mudlee.core.render.texture.TextureRegion;
import org.joml.Vector2f;

/**
 * Renders a single sprite or animation frame via {@link SpriteBatch}.
 *
 * <p>If an {@link Animator} is present on the same {@link hu.mudlee.core.gameobject.GameObject},
 * the current animation frame is used automatically. Otherwise the statically assigned
 * {@link #region} is rendered.
 *
 * <pre>
 * var sr = new SpriteRenderer();
 * sr.scale = 8f;
 * player.addComponent(sr);
 * </pre>
 */
public final class SpriteRenderer extends Component {

    private static final Vector2f ORIGIN = new Vector2f();

    private TextureRegion region;
    public Color color = Color.WHITE;
    public float scale = 1f;
    public boolean flipX;
    public boolean flipY;

    public SpriteRenderer setRegion(TextureRegion region) {
        this.region = region;
        return this;
    }

    public SpriteRenderer setScale(float scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public void draw(GameTime gameTime, SpriteBatch batch) {
        var animator = getComponent(Animator.class);
        var frame = (animator != null) ? animator.getCurrentFrame() : region;
        if (frame == null) {
            return;
        }
        var t = getGameObject().transform;
        batch.draw(frame, t.position, color, t.rotation, ORIGIN, scale * t.scale.x, flipX, flipY);
    }
}
