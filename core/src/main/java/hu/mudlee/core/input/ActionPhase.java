package hu.mudlee.core.input;

/**
 * The lifecycle phase of an {@link InputAction}.
 *
 * <p>Phase transitions for a button action:
 *
 * <pre>
 * DISABLED → (enable) → WAITING
 * WAITING  → (press)  → STARTED  → (held next frame) → PERFORMED
 * PERFORMED → (release) → CANCELED → WAITING
 * </pre>
 */
public enum ActionPhase {

    /** Action is disabled — it will not fire any callbacks. */
    DISABLED,

    /** Action is enabled and waiting for input. */
    WAITING,

    /** Input was detected this frame (initial press). */
    STARTED,

    /** Input is actively held or the action has been triggered. */
    PERFORMED,

    /** Input was released or interrupted this frame. */
    CANCELED
}
