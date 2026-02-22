package hu.mudlee.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A named group of {@link InputAction}s that can be enabled and disabled together.
 *
 * <p>Analogous to Unity's {@code InputActionMap}. Useful for grouping context-specific actions
 * (e.g. {@code "Player"}, {@code "UI"}, {@code "Cutscene"}).
 *
 * <p>Usage:
 *
 * <pre>
 * var playerMap = new InputActionMap("Player");
 *
 * playerMap.addAction("Jump")
 *     .addBinding(Keys.SPACE)
 *     .onPerformed(ctx -> player.jump());
 *
 * playerMap.addAction("Move", ActionType.VECTOR2)
 *     .addCompositeBinding()
 *         .up(Keys.W).down(Keys.S).left(Keys.A).right(Keys.D);
 *
 * playerMap.enable();
 * </pre>
 */
public final class InputActionMap {

    private final String name;
    private final List<InputAction> actions = new ArrayList<>();
    private boolean enabled;

    public InputActionMap(String name) {
        this.name = name;
    }

    /** Creates a new {@link ActionType#BUTTON} action in this map and returns it. */
    public InputAction addAction(String actionName) {
        var action = new InputAction(actionName);
        actions.add(action);
        return action;
    }

    /** Creates a new action of the specified type in this map and returns it. */
    public InputAction addAction(String actionName, ActionType type) {
        var action = new InputAction(actionName, type);
        actions.add(action);
        return action;
    }

    /**
     * Returns the action with the given name, or {@link Optional#empty()} if not found.
     */
    public Optional<InputAction> findAction(String actionName) {
        return actions.stream().filter(a -> a.getName().equals(actionName)).findFirst();
    }

    /** Enables all actions in this map. */
    public void enable() {
        enabled = true;
        actions.forEach(InputAction::enable);
    }

    /** Disables all actions in this map. */
    public void disable() {
        enabled = false;
        actions.forEach(InputAction::disable);
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<InputAction> getActions() {
        return Collections.unmodifiableList(actions);
    }
}
