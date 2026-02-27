package hu.mudlee.core.gameobject;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.SpriteBatch;
import java.util.ArrayList;
import java.util.List;

/**
 * A named container of {@link Component} instances.
 *
 * <p>Every {@code GameObject} owns a {@link Transform} that is always present and cannot be
 * removed. Additional components are attached via {@link #addComponent} and retrieved via
 * {@link #getComponent}.
 *
 * <pre>
 * var player = new GameObject("Player");
 * player.transform.position.set(960, 540);
 * player.addComponent(new SpriteRenderer())
 *       .addComponent(new Animator());
 * player.start();
 * </pre>
 */
public final class GameObject {

    public final String name;
    public final Transform transform = new Transform();

    private final List<Component> components = new ArrayList<>(8);

    public GameObject(String name) {
        this.name = name;
        this.transform.gameObject = this;
    }

    /**
     * Adds a component to this {@code GameObject}. Sets the component's back-reference
     * and adds it to the internal list.
     *
     * @return this {@code GameObject} for fluent chaining
     */
    public GameObject addComponent(Component component) {
        component.gameObject = this;
        components.add(component);
        return this;
    }

    /**
     * Returns the first component matching {@code type}, or {@code null} if none is found.
     * Uses a linear scan — fine for the expected component count per entity (&lt; 10).
     */
    public <T extends Component> T getComponent(Class<T> type) {
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }
        return null;
    }

    /** Returns {@code true} if this {@code GameObject} has a component of the given type. */
    public <T extends Component> boolean hasComponent(Class<T> type) {
        for (int i = 0; i < components.size(); i++) {
            if (type.isInstance(components.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Calls {@link Component#start()} on the transform and every attached component. */
    public void start() {
        transform.start();
        for (int i = 0; i < components.size(); i++) {
            components.get(i).start();
        }
    }

    /** Calls {@link Component#update(GameTime)} on every attached component. */
    public void update(GameTime gameTime) {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).update(gameTime);
        }
    }

    /** Calls {@link Component#draw(GameTime, SpriteBatch)} on every attached component. */
    public void draw(GameTime gameTime, SpriteBatch batch) {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).draw(gameTime, batch);
        }
    }

    /** Disposes all components. Called when this {@code GameObject} is destroyed. */
    public void dispose() {
        for (int i = components.size() - 1; i >= 0; i--) {
            components.get(i).dispose();
        }
        components.clear();
    }
}
