package hu.mudlee.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Vector2f;

/**
 * A named, bindable input action — the Unity-style counterpart to {@link Keyboard#getState()}.
 *
 * <p>An action abstracts away the actual hardware input. Multiple bindings can trigger the same
 * action, and the action fires typed callbacks as it moves through its {@link ActionPhase}.
 *
 * <p>Usage (button):
 *
 * <pre>
 * var jump = new InputAction("Jump")
 *     .addBinding(Keys.SPACE)
 *     .addBinding(MouseButton.LEFT)
 *     .onPerformed(ctx -> player.jump());
 * jump.enable();
 * </pre>
 *
 * <p>Usage (WASD movement):
 *
 * <pre>
 * var move = new InputAction("Move", ActionType.VECTOR2);
 * move.addCompositeBinding()
 *     .up(Keys.W).down(Keys.S).left(Keys.A).right(Keys.D);
 * move.onPerformed(ctx -> player.move(ctx.readVector2()));
 * move.enable();
 * </pre>
 */
public final class InputAction {

    private final String name;
    private final ActionType type;
    private final List<InputBinding> bindings = new ArrayList<>();
    private final List<Consumer<InputActionContext>> startedCallbacks = new ArrayList<>();
    private final List<Consumer<InputActionContext>> performedCallbacks = new ArrayList<>();
    private final List<Consumer<InputActionContext>> canceledCallbacks = new ArrayList<>();

    private ActionPhase phase = ActionPhase.DISABLED;
    private boolean enabled;

    /** Creates a {@link ActionType#BUTTON} action with the given name. */
    public InputAction(String name) {
        this(name, ActionType.BUTTON);
    }

    public InputAction(String name, ActionType type) {
        this.name = name;
        this.type = type;
    }

    /** Adds a keyboard key binding to this action. Returns {@code this} for chaining. */
    public InputAction addBinding(Keys key) {
        bindings.add(InputBinding.of(key));
        return this;
    }

    /** Adds a mouse button binding to this action. Returns {@code this} for chaining. */
    public InputAction addBinding(MouseButton button) {
        bindings.add(InputBinding.of(button));
        return this;
    }

    /**
     * Adds a 2D composite binding and returns it for fluent configuration. Intended for
     * {@link ActionType#VECTOR2} actions.
     */
    public InputBinding.Vector2CompositeBinding addCompositeBinding() {
        var composite = InputBinding.vector2();
        bindings.add(composite);
        return composite;
    }

    /** Registers a callback invoked when the action enters {@link ActionPhase#STARTED}. */
    public InputAction onStarted(Consumer<InputActionContext> callback) {
        startedCallbacks.add(callback);
        return this;
    }

    /** Registers a callback invoked when the action enters {@link ActionPhase#PERFORMED}. */
    public InputAction onPerformed(Consumer<InputActionContext> callback) {
        performedCallbacks.add(callback);
        return this;
    }

    /** Registers a callback invoked when the action enters {@link ActionPhase#CANCELED}. */
    public InputAction onCanceled(Consumer<InputActionContext> callback) {
        canceledCallbacks.add(callback);
        return this;
    }

    /** Enables the action, registering it with the {@link InputSystem}. */
    public void enable() {
        if (!enabled) {
            enabled = true;
            phase = ActionPhase.WAITING;
            InputSystem.register(this);
        }
    }

    /** Disables the action, removing it from the {@link InputSystem}. */
    public void disable() {
        if (enabled) {
            enabled = false;
            phase = ActionPhase.DISABLED;
            InputSystem.unregister(this);
        }
    }

    public String getName() {
        return name;
    }

    public ActionType getType() {
        return type;
    }

    public ActionPhase getPhase() {
        return phase;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns {@code true} when the action is in {@link ActionPhase#STARTED} or {@link ActionPhase#PERFORMED}. */
    public boolean isPressed() {
        return phase == ActionPhase.STARTED || phase == ActionPhase.PERFORMED;
    }

    /**
     * Reads the current float value of this action. Always returns {@code 0.0} for
     * {@link ActionType#BUTTON} actions. Returns the magnitude for {@link ActionType#VALUE}.
     */
    public float readFloat() {
        return InputSystem.readFloat(this);
    }

    /**
     * Reads the current 2D vector value of this action. Only meaningful for
     * {@link ActionType#VECTOR2} actions with a {@link InputBinding.Vector2CompositeBinding}.
     */
    public Vector2f readVector2() {
        return InputSystem.readVector2(this);
    }

    List<InputBinding> bindings() {
        return Collections.unmodifiableList(bindings);
    }

    void transitionTo(ActionPhase newPhase) {
        this.phase = newPhase;
        var ctx = new InputActionContext(this, newPhase);
        switch (newPhase) {
            case STARTED -> startedCallbacks.forEach(cb -> cb.accept(ctx));
            case PERFORMED -> performedCallbacks.forEach(cb -> cb.accept(ctx));
            case CANCELED -> canceledCallbacks.forEach(cb -> cb.accept(ctx));
            default -> {}
        }
    }
}
