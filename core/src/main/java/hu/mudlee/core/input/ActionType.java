package hu.mudlee.core.input;

/**
 * Defines what kind of value an {@link InputAction} produces.
 *
 * <ul>
 *   <li>{@link #BUTTON} — digital on/off. Fires started → performed → canceled callbacks.
 *   <li>{@link #VALUE} — analog float (e.g. trigger axis). Read via
 *       {@link InputActionContext#readFloat()}.
 *   <li>{@link #VECTOR2} — 2D directional input from a composite binding (e.g. WASD). Read via
 *       {@link InputActionContext#readVector2()}.
 * </ul>
 */
public enum ActionType {
    BUTTON,
    VALUE,
    VECTOR2
}
