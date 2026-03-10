# Plan 011 — Gamepad Support via GLFW Gamepad API

## Goal

Add controller input support using GLFW's standardized Gamepad API
(`glfwGetGamepadState`). The same `InputAction` / `InputActionMap` abstraction used
for keyboard and mouse must work for gamepads — game code should be able to bind
multiple inputs to one action with zero system-level changes.

---

## GLFW Gamepad API Primer

GLFW normalizes all gamepads through SDL_GameControllerDB, so the same button/axis
layout works across Xbox, PS4/PS5, and most common controllers.

**Key functions:**
- `glfwJoystickIsGamepad(int jid)` — true when the joystick has a known standard mapping
- `glfwGetGamepadState(int jid, GLFWGamepadState state)` — fills a struct with 15 buttons
  and 6 axes; returns `GLFW_TRUE` if the controller is connected
- `glfwSetJoystickCallback(callback)` — fires on connect/disconnect (event-driven)

**Axes convention (important):**
- `LEFT_X` / `RIGHT_X`: −1.0 = left, +1.0 = right ✓ matches engine convention
- `LEFT_Y` / `RIGHT_Y`: −1.0 = **up**, +1.0 = **down** ✗ inverted — must be negated
  in `InputSystem` so game code always receives +Y = up
- `LEFT_TRIGGER` / `RIGHT_TRIGGER`: 0.0 = released, +1.0 = fully pressed

**Polling model:** There are no per-button callbacks. The gamepad state must be polled
every frame, and press/release transitions detected by diffing current vs. previous state.

---

## Design Decisions

### Single primary gamepad
Support one active gamepad (the first connected slot found). Extend to multi-gamepad
later if needed — the internal plumbing will not prevent it.

### Stick deadzone
Apply a constant deadzone of `0.15f` inside `InputSystem` when reading axis values.
Values within the deadzone are clamped to `0.0f` to eliminate stick drift.

### Analog stick as VECTOR2 composite
The left/right analog sticks are exposed as a new `GamepadStickCompositeBinding`
(parallel to the existing keyboard `Vector2CompositeBinding`). Multiple composite
bindings on the same VECTOR2 action are already supported — `computeVector2` returns
the first non-zero vector, so keyboard and stick can coexist on one `Move` action.

### Axis inversion hidden from game code
The GLFW Y-axis inversion is corrected in `InputSystem`, not exposed to the caller.
Game code always works with +Y = up.

---

## New Files

### 1. `GamepadButton` — enum of 15 standardized buttons

**File:** `core/.../input/GamepadButton.java`

```java
public enum GamepadButton {
    A, B, X, Y,
    LEFT_BUMPER, RIGHT_BUMPER,
    BACK, START, GUIDE,
    LEFT_THUMB, RIGHT_THUMB,
    DPAD_UP, DPAD_RIGHT, DPAD_DOWN, DPAD_LEFT;

    // GLFW_GAMEPAD_BUTTON_* values are 0–14 in declaration order above
    public int glfwCode() { return ordinal(); }
}
```

---

### 2. `GamepadAxis` — enum of 6 standardized axes

**File:** `core/.../input/GamepadAxis.java`

```java
public enum GamepadAxis {
    LEFT_X, LEFT_Y,
    RIGHT_X, RIGHT_Y,
    LEFT_TRIGGER, RIGHT_TRIGGER;

    // GLFW_GAMEPAD_AXIS_* values are 0–5 in declaration order above
    public int glfwCode() { return ordinal(); }
}
```

---

### 3. `GamepadState` — immutable per-frame snapshot

**File:** `core/.../input/GamepadState.java`

```java
public final class GamepadState {

    private final boolean[] buttons;  // length 15
    private final float[] axes;       // length 6

    GamepadState(boolean[] buttons, float[] axes) {
        this.buttons = buttons;
        this.axes = axes;
    }

    public boolean isButtonDown(GamepadButton button) {
        return buttons[button.glfwCode()];
    }

    public float getAxis(GamepadAxis axis) {
        return axes[axis.glfwCode()];
    }
}
```

---

### 4. `Gamepad` — public polling API (mirrors `Keyboard` / `Mouse`)

**File:** `core/.../input/Gamepad.java`

```java
public final class Gamepad {

    private Gamepad() {}

    /** Returns a snapshot of the current gamepad state. */
    public static GamepadState getState() {
        return InputSystem.getGamepadState();
    }

    /** True if at least one gamepad is connected. */
    public static boolean isConnected() {
        return InputSystem.isGamepadConnected();
    }
}
```

---

## Modified Files

### 5. `InputBinding` — two new binding types

Extend the sealed interface with `GamepadButtonBinding` and `GamepadStickCompositeBinding`.

```java
public sealed interface InputBinding
        permits InputBinding.KeyBinding,
                InputBinding.MouseButtonBinding,
                InputBinding.Vector2CompositeBinding,
                InputBinding.GamepadButtonBinding,
                InputBinding.GamepadStickCompositeBinding {

    // ... existing types unchanged ...

    /** A single gamepad button binding. */
    record GamepadButtonBinding(GamepadButton button) implements InputBinding {}

    /**
     * Maps a gamepad's X and Y axes to a 2D vector.
     * Y-axis inversion (GLFW convention) is applied automatically in InputSystem.
     */
    record GamepadStickCompositeBinding(GamepadAxis xAxis, GamepadAxis yAxis)
            implements InputBinding {}

    static InputBinding of(GamepadButton button) {
        return new GamepadButtonBinding(button);
    }
}
```

---

### 6. `InputAction` — new binding factory methods

```java
/** Adds a gamepad button binding to this action. Returns {@code this} for chaining. */
public InputAction addBinding(GamepadButton button) {
    bindings.add(InputBinding.of(button));
    return this;
}

/**
 * Adds a gamepad stick composite binding. Intended for {@link ActionType#VECTOR2} actions.
 * Returns {@code this} for chaining.
 */
public InputAction addStickCompositeBinding(GamepadAxis xAxis, GamepadAxis yAxis) {
    bindings.add(new InputBinding.GamepadStickCompositeBinding(xAxis, yAxis));
    return this;
}
```

---

### 7. `InputSystem` — gamepad polling + phase transitions

The most significant change. Three areas:

**New state arrays:**

```java
private static final boolean[] GAMEPAD_BUTTON_STATE     = new boolean[15];
private static final boolean[] PREV_GAMEPAD_BUTTON_STATE = new boolean[15];
private static final float[]   GAMEPAD_AXIS_STATE        = new float[6];
private static final float     STICK_DEADZONE            = 0.15f;
private static int activePadId = -1;  // -1 = none connected
```

**`update()` — poll gamepad before processing actions:**

```java
public static void update() {
    scrollX = 0f;
    scrollY = 0f;
    pollGamepad();              // <-- new: diff state, fire button transitions
    for (var action : activeActions) {
        // ... existing BUTTON STARTED→PERFORMED and VECTOR2 logic unchanged ...
    }
}
```

**New `pollGamepad()` private method:**

```java
private static void pollGamepad() {
    if (activePadId == -1) { return; }
    try (var stack = MemoryStack.stackPush()) {
        var state = GLFWGamepadState.malloc(stack);
        if (glfwGetGamepadState(activePadId, state) != GLFW_TRUE) { return; }

        System.arraycopy(GAMEPAD_BUTTON_STATE, 0, PREV_GAMEPAD_BUTTON_STATE, 0, 15);
        for (int i = 0; i < 15; i++) {
            GAMEPAD_BUTTON_STATE[i] = state.buttons(i) == GLFW_PRESS;
        }
        for (int i = 0; i < 6; i++) {
            var raw = state.axes(i);
            GAMEPAD_AXIS_STATE[i] = Math.abs(raw) < STICK_DEADZONE ? 0f : raw;
        }

        // Fire BUTTON phase transitions for gamepad button press/release
        for (int i = 0; i < 15; i++) {
            var pressed    = GAMEPAD_BUTTON_STATE[i];
            var wasPressed = PREV_GAMEPAD_BUTTON_STATE[i];
            if (pressed == wasPressed) { continue; }
            var button = GamepadButton.values()[i];
            if (pressed) {
                processGamepadButtonPressed(button);
            } else {
                processGamepadButtonReleased(button);
            }
        }
    }
}
```

**`computeVector2()` — handle `GamepadStickCompositeBinding`:**

The existing method already iterates bindings and returns the first non-zero vector.
Add a branch for the new binding type:

```java
if (binding instanceof InputBinding.GamepadStickCompositeBinding stick) {
    var x = GAMEPAD_AXIS_STATE[stick.xAxis().glfwCode()];
    var y = -GAMEPAD_AXIS_STATE[stick.yAxis().glfwCode()];  // invert GLFW Y
    if (x != 0f || y != 0f) {
        return new Vector2f(x, y);
    }
}
```

**`isAnyBindingActive()` — add gamepad button check:**

```java
if (binding instanceof InputBinding.GamepadButtonBinding gb
        && GAMEPAD_BUTTON_STATE[gb.button().glfwCode()]) {
    return true;
}
```

**New package-visible accessors:**

```java
static GamepadState getGamepadState() {
    return new GamepadState(
        Arrays.copyOf(GAMEPAD_BUTTON_STATE, 15),
        Arrays.copyOf(GAMEPAD_AXIS_STATE, 6));
}

static boolean isGamepadConnected() { return activePadId != -1; }
```

---

### 8. `Window` — joystick connect/disconnect callback

Register once during `init()`:

```java
glfwSetJoystickCallback((jid, event) -> {
    if (event == GLFW_CONNECTED && glfwJoystickIsGamepad(jid)) {
        InputSystem.onGamepadConnected(jid);
        log.info("Gamepad connected: {} (slot {})", glfwGetGamepadName(jid), jid);
    } else if (event == GLFW_DISCONNECTED) {
        InputSystem.onGamepadDisconnected(jid);
        log.info("Gamepad disconnected (slot {})", jid);
    }
});
```

Also scan for already-connected gamepads during `init()` (connecting before the callback
is registered):

```java
for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; jid++) {
    if (glfwJoystickPresent(jid) && glfwJoystickIsGamepad(jid)) {
        InputSystem.onGamepadConnected(jid);
        break;
    }
}
```

---

## Resulting Game Code

`PlayerScene` — add stick binding alongside keyboard with no changes to the system:

```java
var moveAction = actions.addAction("Move", ActionType.VECTOR2);
moveAction.addCompositeBinding()
    .up(Keys.UP).down(Keys.DOWN).left(Keys.LEFT).right(Keys.RIGHT);
moveAction.addStickCompositeBinding(GamepadAxis.LEFT_X, GamepadAxis.LEFT_Y);

var attackAction = actions.addAction("Attack")
    .addBinding(Keys.SPACE)
    .addBinding(GamepadButton.A);

var dieAction = actions.addAction("Die")
    .addBinding(Keys.X)
    .addBinding(GamepadButton.B);
```

`PlayerControlSystem` — **zero changes required.**

---

## File Checklist

```
NEW  core/.../input/GamepadButton.java
NEW  core/.../input/GamepadAxis.java
NEW  core/.../input/GamepadState.java
NEW  core/.../input/Gamepad.java
MOD  core/.../input/InputBinding.java     (2 new permits + binding types + factory)
MOD  core/.../input/InputAction.java      (addBinding(GamepadButton), addStickCompositeBinding)
MOD  core/.../input/InputSystem.java      (polling, state arrays, computeVector2, isAnyBindingActive)
MOD  core/src/main/java/module-info.java  (export Gamepad, GamepadButton, GamepadAxis, GamepadState)
MOD  core/.../window/Window.java          (joystick callback + startup scan)
```
