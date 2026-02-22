package hu.mudlee.core.input;

/**
 * Represents a hardware input binding attached to an {@link InputAction}.
 *
 * <p>Use the static factory methods to create bindings:
 *
 * <pre>
 * InputBinding.of(Keys.SPACE)
 * InputBinding.of(MouseButton.LEFT)
 * InputBinding.vector2().up(Keys.W).down(Keys.S).left(Keys.A).right(Keys.D)
 * </pre>
 */
public sealed interface InputBinding permits InputBinding.KeyBinding, InputBinding.MouseButtonBinding, InputBinding.Vector2CompositeBinding {

    /** A single keyboard key binding. */
    record KeyBinding(Keys key) implements InputBinding {}

    /** A single mouse button binding. */
    record MouseButtonBinding(MouseButton button) implements InputBinding {}

    /**
     * A composite 2D-vector binding that maps four directional keys to a {@link org.joml.Vector2f}.
     * Typically used for movement (WASD or arrow keys).
     *
     * <p>Usage:
     *
     * <pre>
     * moveAction.addCompositeBinding()
     *     .up(Keys.W)
     *     .down(Keys.S)
     *     .left(Keys.A)
     *     .right(Keys.D);
     * </pre>
     */
    final class Vector2CompositeBinding implements InputBinding {

        private Keys up;
        private Keys down;
        private Keys left;
        private Keys right;

        Vector2CompositeBinding() {}

        public Vector2CompositeBinding up(Keys key) {
            this.up = key;
            return this;
        }

        public Vector2CompositeBinding down(Keys key) {
            this.down = key;
            return this;
        }

        public Vector2CompositeBinding left(Keys key) {
            this.left = key;
            return this;
        }

        public Vector2CompositeBinding right(Keys key) {
            this.right = key;
            return this;
        }

        public Keys up() {
            return up;
        }

        public Keys down() {
            return down;
        }

        public Keys left() {
            return left;
        }

        public Keys right() {
            return right;
        }
    }

    /** Creates a keyboard key binding. */
    static InputBinding of(Keys key) {
        return new KeyBinding(key);
    }

    /** Creates a mouse button binding. */
    static InputBinding of(MouseButton button) {
        return new MouseButtonBinding(button);
    }

    /** Creates a new, unconfigured 2D composite binding. Configure it via the fluent setters. */
    static Vector2CompositeBinding vector2() {
        return new Vector2CompositeBinding();
    }
}
