package hu.mudlee.core.gameobject;

import org.joml.Vector2f;

/**
 * Built-in component that holds an entity's 2D spatial state.
 *
 * <p>Every {@link GameObject} always has exactly one {@code Transform2D}; it is created
 * automatically in the {@code GameObject} constructor and cannot be removed.
 *
 * <p>{@code Transform2D} is pure data — it overrides no lifecycle methods. Other components
 * read and write its fields directly via {@code gameObject.transform}.
 */
public final class Transform2D extends Component {

    public final Vector2f position = new Vector2f();
    public float rotation;
    public final Vector2f scale = new Vector2f(1f, 1f);
}
