package hu.mudlee.core.input;

import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

/**
 * Internal hub that drives the input system each frame.
 *
 * <p><strong>This class is not part of the public API.</strong> Game code should use
 * {@link Keyboard}, {@link Mouse}, {@link Gamepad}, {@link InputAction}, and
 * {@link InputActionMap} instead.
 */
public final class InputSystem {

    private static final boolean[] KEY_STATE = new boolean[Keys.values().length];
    private static final boolean[] MOUSE_STATE = new boolean[MouseButton.values().length];
    private static final boolean[] GAMEPAD_BUTTON_STATE = new boolean[15];
    private static final boolean[] PREV_GAMEPAD_BUTTON_STATE = new boolean[15];
    private static final float[] GAMEPAD_AXIS_STATE = new float[6];
    private static final float STICK_DEADZONE = 0.15f;
    private static final Vector2f REUSABLE_VECTOR2 = new Vector2f();

    private static float mouseX;
    private static float mouseY;
    private static float scrollX;
    private static float scrollY;
    private static int activePadId = -1;

    private static final List<InputAction> activeActions = new ArrayList<>();

    private InputSystem() {}

    static KeyboardState getKeyboardState() {
        return new KeyboardState(KEY_STATE);
    }

    static MouseState getMouseState() {
        return new MouseState(mouseX, mouseY, scrollX, scrollY, MOUSE_STATE);
    }

    static GamepadState getGamepadState() {
        return new GamepadState(
                Arrays.copyOf(GAMEPAD_BUTTON_STATE, GAMEPAD_BUTTON_STATE.length),
                Arrays.copyOf(GAMEPAD_AXIS_STATE, GAMEPAD_AXIS_STATE.length));
    }

    static boolean isGamepadConnected() {
        return activePadId != -1;
    }

    public static void onGamepadConnected(int jid) {
        if (activePadId == -1) {
            activePadId = jid;
        }
    }

    public static void onGamepadDisconnected(int jid) {
        if (activePadId == jid) {
            activePadId = -1;
            Arrays.fill(GAMEPAD_BUTTON_STATE, false);
            Arrays.fill(PREV_GAMEPAD_BUTTON_STATE, false);
            Arrays.fill(GAMEPAD_AXIS_STATE, 0f);
        }
    }

    static float readFloat(InputAction action) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.KeyBinding kb
                    && KEY_STATE[kb.key().ordinal()]) {
                return 1f;
            }
            if (binding instanceof InputBinding.MouseButtonBinding mb
                    && MOUSE_STATE[mb.button().ordinal()]) {
                return 1f;
            }
            if (binding instanceof InputBinding.GamepadButtonBinding gb
                    && GAMEPAD_BUTTON_STATE[gb.button().glfwCode()]) {
                return 1f;
            }
        }
        return 0f;
    }

    static Vector2f readVector2(InputAction action) {
        return computeVector2(action);
    }

    static void register(InputAction action) {
        if (!activeActions.contains(action)) {
            activeActions.add(action);
        }
    }

    static void unregister(InputAction action) {
        activeActions.remove(action);
    }

    /**
     * Called once per frame <em>before</em> {@code Window.pollEvents()}.
     * Polls the gamepad, resets per-frame scroll, then advances BUTTON actions from
     * STARTED → PERFORMED and drives VECTOR2 composite actions.
     */
    public static void update() {
        scrollX = 0f;
        scrollY = 0f;
        pollGamepad();
        for (var action : activeActions) {
            if (action.getType() == ActionType.BUTTON && action.getPhase() == ActionPhase.STARTED) {
                action.transitionTo(ActionPhase.PERFORMED);
            } else if (action.getType() == ActionType.VECTOR2) {
                updateVector2Action(action);
            }
        }
    }

    /** Wired to GLFW key callback from {@code Window}. */
    public static void processKey(int glfwKeyCode, int glfwAction) {
        var key = Keys.fromGlfwCode(glfwKeyCode);
        if (key == null) {
            return;
        }
        var pressed = glfwAction == GLFW_PRESS || glfwAction == GLFW_REPEAT;
        var wasPressed = KEY_STATE[key.ordinal()];
        KEY_STATE[key.ordinal()] = pressed;

        if (pressed && !wasPressed) {
            for (var action : activeActions) {
                if (action.getType() != ActionType.BUTTON || action.getPhase() != ActionPhase.WAITING) {
                    continue;
                }
                for (var binding : action.bindings()) {
                    if (binding instanceof InputBinding.KeyBinding kb && kb.key() == key) {
                        action.transitionTo(ActionPhase.STARTED);
                        break;
                    }
                }
            }
        } else if (!pressed && wasPressed) {
            for (var action : activeActions) {
                if (action.getType() != ActionType.BUTTON) {
                    continue;
                }
                var phase = action.getPhase();
                if (phase != ActionPhase.STARTED && phase != ActionPhase.PERFORMED) {
                    continue;
                }
                if (isBoundToKey(action, key) && !isAnyBindingActive(action)) {
                    action.transitionTo(ActionPhase.CANCELED);
                    action.transitionTo(ActionPhase.WAITING);
                }
            }
        }
    }

    /** Wired to GLFW mouse button callback from {@code Window}. */
    public static void processMouseButton(int glfwButton, int glfwAction) {
        var button = MouseButton.fromGlfwCode(glfwButton);
        if (button == null) {
            return;
        }
        var pressed = glfwAction == GLFW_PRESS;
        var wasPressed = MOUSE_STATE[button.ordinal()];
        MOUSE_STATE[button.ordinal()] = pressed;

        if (pressed && !wasPressed) {
            for (var action : activeActions) {
                if (action.getType() != ActionType.BUTTON || action.getPhase() != ActionPhase.WAITING) {
                    continue;
                }
                for (var binding : action.bindings()) {
                    if (binding instanceof InputBinding.MouseButtonBinding mb && mb.button() == button) {
                        action.transitionTo(ActionPhase.STARTED);
                        break;
                    }
                }
            }
        } else if (!pressed && wasPressed) {
            for (var action : activeActions) {
                if (action.getType() != ActionType.BUTTON) {
                    continue;
                }
                var phase = action.getPhase();
                if (phase != ActionPhase.STARTED && phase != ActionPhase.PERFORMED) {
                    continue;
                }
                if (isBoundToMouseButton(action, button) && !isAnyBindingActive(action)) {
                    action.transitionTo(ActionPhase.CANCELED);
                    action.transitionTo(ActionPhase.WAITING);
                }
            }
        }
    }

    /** Wired to GLFW cursor position callback from {@code Window}. */
    public static void processMouseMove(double x, double y) {
        mouseX = (float) x;
        mouseY = (float) y;
    }

    /** Wired to GLFW scroll callback from {@code Window}. Scroll is accumulated until reset in {@link #update()}. */
    public static void processMouseScroll(double xOffset, double yOffset) {
        scrollX += (float) xOffset;
        scrollY += (float) yOffset;
    }

    private static void pollGamepad() {
        if (activePadId == -1) {
            return;
        }
        try (var stack = MemoryStack.stackPush()) {
            var state = GLFWGamepadState.malloc(stack);
            if (!glfwGetGamepadState(activePadId, state)) {
                return;
            }
            System.arraycopy(GAMEPAD_BUTTON_STATE, 0, PREV_GAMEPAD_BUTTON_STATE, 0, GAMEPAD_BUTTON_STATE.length);
            for (int i = 0; i < GAMEPAD_BUTTON_STATE.length; i++) {
                GAMEPAD_BUTTON_STATE[i] = state.buttons(i) == GLFW_PRESS;
            }
            for (int i = 0; i < GAMEPAD_AXIS_STATE.length; i++) {
                var raw = state.axes(i);
                GAMEPAD_AXIS_STATE[i] = Math.abs(raw) < STICK_DEADZONE ? 0f : raw;
            }
            for (int i = 0; i < GAMEPAD_BUTTON_STATE.length; i++) {
                var pressed = GAMEPAD_BUTTON_STATE[i];
                var wasPressed = PREV_GAMEPAD_BUTTON_STATE[i];
                if (pressed == wasPressed) {
                    continue;
                }
                var button = GamepadButton.values()[i];
                if (pressed) {
                    processGamepadButtonPressed(button);
                } else {
                    processGamepadButtonReleased(button);
                }
            }
        }
    }

    private static void processGamepadButtonPressed(GamepadButton button) {
        for (var action : activeActions) {
            if (action.getType() != ActionType.BUTTON || action.getPhase() != ActionPhase.WAITING) {
                continue;
            }
            for (var binding : action.bindings()) {
                if (binding instanceof InputBinding.GamepadButtonBinding gb && gb.button() == button) {
                    action.transitionTo(ActionPhase.STARTED);
                    break;
                }
            }
        }
    }

    private static void processGamepadButtonReleased(GamepadButton button) {
        for (var action : activeActions) {
            if (action.getType() != ActionType.BUTTON) {
                continue;
            }
            var phase = action.getPhase();
            if (phase != ActionPhase.STARTED && phase != ActionPhase.PERFORMED) {
                continue;
            }
            if (isBoundToGamepadButton(action, button) && !isAnyBindingActive(action)) {
                action.transitionTo(ActionPhase.CANCELED);
                action.transitionTo(ActionPhase.WAITING);
            }
        }
    }

    private static void updateVector2Action(InputAction action) {
        var vec = computeVector2(action);
        var active = vec.x != 0f || vec.y != 0f;
        var phase = action.getPhase();
        if (active && phase == ActionPhase.WAITING) {
            action.transitionTo(ActionPhase.STARTED);
            action.transitionTo(ActionPhase.PERFORMED);
        } else if (!active && (phase == ActionPhase.STARTED || phase == ActionPhase.PERFORMED)) {
            action.transitionTo(ActionPhase.CANCELED);
            action.transitionTo(ActionPhase.WAITING);
        }
    }

    private static Vector2f computeVector2(InputAction action) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.Vector2CompositeBinding composite) {
                float x = 0f;
                float y = 0f;
                if (composite.right() != null && KEY_STATE[composite.right().ordinal()]) {
                    x += 1f;
                }
                if (composite.left() != null && KEY_STATE[composite.left().ordinal()]) {
                    x -= 1f;
                }
                if (composite.up() != null && KEY_STATE[composite.up().ordinal()]) {
                    y += 1f;
                }
                if (composite.down() != null && KEY_STATE[composite.down().ordinal()]) {
                    y -= 1f;
                }
                if (x != 0f || y != 0f) {
                    return REUSABLE_VECTOR2.set(x, y);
                }
            }
            if (binding instanceof InputBinding.GamepadStickCompositeBinding stick) {
                var x = GAMEPAD_AXIS_STATE[stick.xAxis().glfwCode()];
                var y = -GAMEPAD_AXIS_STATE[stick.yAxis().glfwCode()]; // invert GLFW Y convention
                if (x != 0f || y != 0f) {
                    return REUSABLE_VECTOR2.set(x, y);
                }
            }
        }
        return REUSABLE_VECTOR2.set(0f, 0f);
    }

    private static boolean isAnyBindingActive(InputAction action) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.KeyBinding kb
                    && KEY_STATE[kb.key().ordinal()]) {
                return true;
            }
            if (binding instanceof InputBinding.MouseButtonBinding mb
                    && MOUSE_STATE[mb.button().ordinal()]) {
                return true;
            }
            if (binding instanceof InputBinding.GamepadButtonBinding gb
                    && GAMEPAD_BUTTON_STATE[gb.button().glfwCode()]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundToKey(InputAction action, Keys key) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.KeyBinding kb && kb.key() == key) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundToMouseButton(InputAction action, MouseButton button) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.MouseButtonBinding mb && mb.button() == button) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundToGamepadButton(InputAction action, GamepadButton button) {
        for (var binding : action.bindings()) {
            if (binding instanceof InputBinding.GamepadButtonBinding gb && gb.button() == button) {
                return true;
            }
        }
        return false;
    }
}
