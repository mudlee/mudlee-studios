package hu.mudlee.core.gameobject;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.SpriteBatch;

/**
 * Base class for all entity-level components attached to a {@link GameObject}.
 *
 * <p>This is <strong>not</strong> the same as {@link hu.mudlee.core.GameComponent}, which is a
 * game-level service plugged into the {@link hu.mudlee.core.Game} loop. A {@code Component}
 * belongs to a single {@code GameObject} and participates in that object's per-frame lifecycle.
 *
 * <p>Lifecycle (called by {@link GameObject}):
 * <ol>
 *   <li>{@link #start()} — once, when {@link GameObject#start()} is called
 *   <li>{@link #update(GameTime)} — every frame
 *   <li>{@link #draw(GameTime, SpriteBatch)} — every frame, after all updates
 *   <li>{@link #dispose()} — when the component or its {@code GameObject} is destroyed
 * </ol>
 *
 * <p>Override only the methods you need; all have empty default implementations.
 */
public abstract class Component {

    GameObject gameObject;

    /** Called once when {@link GameObject#start()} is invoked. */
    public void start() {}

    /** Called every frame by {@link GameObject#update(GameTime)}. */
    public void update(GameTime gameTime) {}

    /** Called every frame by {@link GameObject#draw(GameTime, SpriteBatch)}, after all updates. */
    public void draw(GameTime gameTime, SpriteBatch batch) {}

    /** Called when this component is removed or its {@link GameObject} is destroyed. */
    public void dispose() {}

    public GameObject getGameObject() {
        return gameObject;
    }

    /** Convenience shorthand for {@code gameObject.getComponent(type)}. */
    protected <T extends Component> T getComponent(Class<T> type) {
        if (gameObject == null) {
            return null;
        }
        return gameObject.getComponent(type);
    }
}
