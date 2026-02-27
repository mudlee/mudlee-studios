package hu.mudlee.core.gameobject.components;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.gameobject.Component;
import hu.mudlee.core.render.animation.Animation2D;
import hu.mudlee.core.render.animation.AnimationPlayer2D;
import hu.mudlee.core.render.texture.TextureRegion;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages named {@link Animation2D} clips and drives an {@link AnimationPlayer2D}.
 *
 * <p>Attach to a {@link hu.mudlee.core.gameobject.GameObject} alongside a
 * {@link SpriteRenderer2D}. The renderer reads the current frame automatically.
 *
 * <pre>
 * var animator = new Animator2D()
 *     .add("IdleRight", sheet.createAnimation("IdleRight", 1, 0, 6, 0.12f, PlayMode.LOOP))
 *     .add("WalkRight", sheet.createAnimation("WalkRight", 4, 0, 6, 0.08f, PlayMode.LOOP));
 * animator.play("IdleRight");
 * </pre>
 */
public final class Animator2D extends Component {

    private final AnimationPlayer2D player = new AnimationPlayer2D();
    private final Map<String, Animation2D> clips = new LinkedHashMap<>();

    /** Registers a named animation clip. Returns {@code this} for fluent chaining. */
    public Animator2D addAnimation(String name, Animation2D clip) {
        clips.put(name, clip);
        return this;
    }

    /** Switches to the named animation. If already playing, this is a no-op. */
    public void play(String name) {
        var clip = clips.get(name);
        if (clip != null) {
            player.play(clip);
        }
    }

    /** Returns the {@link TextureRegion} for the current animation frame, or {@code null}. */
    public TextureRegion getCurrentFrame() {
        return player.getCurrentFrame();
    }

    /** Returns {@code true} when a {@code PlayMode.ONCE} animation has finished. */
    public boolean isFinished() {
        return player.isFinished();
    }

    @Override
    public void update(GameTime gameTime) {
        player.update(gameTime);
    }
}
