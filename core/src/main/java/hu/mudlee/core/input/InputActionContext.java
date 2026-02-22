package hu.mudlee.core.input;

import org.joml.Vector2f;

/**
 * Context object passed to {@link InputAction} phase callbacks (started, performed, canceled).
 *
 * <p>Usage:
 *
 * <pre>
 * jumpAction.onPerformed(ctx -> {
 *     System.out.println("Jump! action=" + ctx.actionName());
 * });
 *
 * moveAction.onPerformed(ctx -> {
 *     Vector2f dir = ctx.readVector2();
 *     player.move(dir);
 * });
 * </pre>
 */
public final class InputActionContext {

    private final InputAction action;
    private final ActionPhase phase;

    InputActionContext(InputAction action, ActionPhase phase) {
        this.action = action;
        this.phase = phase;
    }

    /** The action that triggered this callback. */
    public InputAction action() {
        return action;
    }

    /** The phase the action transitioned into. */
    public ActionPhase phase() {
        return phase;
    }

    /** Shorthand for {@code action().getName()}. */
    public String actionName() {
        return action.getName();
    }

    /**
     * Reads the action's current value as a boolean (true when STARTED or PERFORMED).
     * Intended for {@link ActionType#BUTTON} actions.
     */
    public boolean readBoolean() {
        return action.isPressed();
    }

    /**
     * Reads the action's current value as a float.
     * Intended for {@link ActionType#VALUE} actions.
     */
    public float readFloat() {
        return action.readFloat();
    }

    /**
     * Reads the action's current value as a 2D vector.
     * Intended for {@link ActionType#VECTOR2} actions with a {@link InputBinding.Vector2CompositeBinding}.
     */
    public Vector2f readVector2() {
        return action.readVector2();
    }
}
