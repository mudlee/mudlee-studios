# Plan 008 — Dead Method Audit

This document lists every method found to have zero call sites in the current codebase.
All findings are grounded in a specific file and line. Where deleting a method would
make a second method orphaned, the cascade is documented.

The list is split into two categories:

- **Truly dead** — methods that have zero callers anywhere (engine + sandbox + tests).
- **API surface only** — public methods whose only purpose is to be called by engine
  consumers; the current sandbox never calls them, but they are intentional public API.
  These are lower priority to remove, but worth discussing.

---

## Truly Dead Methods

### 1. `Texture2D.unBind()`

**File:** `core/src/main/java/hu/mudlee/core/render/texture/Texture2D.java:32`

```java
public abstract void unBind();
```

`bind()` is called in many places; `unBind()` is declared but never invoked anywhere in
the codebase. `VulkanTexture2D` implements it, but nothing calls it.

**Cascade:** Removing the abstract declaration also removes the override in
`VulkanTexture2D`.

---

### 2. `VertexArray.setEBO(ElementBuffer)`

**File:** `core/src/main/java/hu/mudlee/core/render/VertexArray.java:22`

```java
public abstract void setEBO(ElementBuffer elementBuffer);
```

EBOs are always passed at construction time in `VulkanVertexArray`.
The setter abstraction is never used from outside.

**Cascade:** Removing the abstract declaration removes the implementation in
`VulkanVertexArray`.

---

### 3. `VertexArray.setInstanceCount(int)`

**File:** `core/src/main/java/hu/mudlee/core/render/VertexArray.java:24`

```java
public abstract void setInstanceCount(int count);
```

GPU instancing infrastructure exists (`getInstanceCount()`, `isInstanced()` are used by
the rendering context), but the setter is never called — instance count is never changed
from outside.

**Cascade:** Removes the implementation in `VulkanVertexArray:67`.

---

### 4. `VertexBuffer.update(ByteBuffer, int)`

**File:** `core/src/main/java/hu/mudlee/core/render/VertexBuffer.java:26`

```java
public void update(ByteBuffer data, int byteCount) {
    throw new UnsupportedOperationException(...);
}
```

The `float[]` overload `update(float[], int)` is used by `SpriteBatch2D`. The
`ByteBuffer` overload is never called. `VulkanVertexBuffer:108` overrides it.

**Cascade:** Removing the base declaration and the override is safe.

---

### 5. `ElementBuffer.update(ByteBuffer, int)`

**File:** `core/src/main/java/hu/mudlee/core/render/ElementBuffer.java:29`

```java
public void update(ByteBuffer data, int byteCount) {
    throw new UnsupportedOperationException(...);
}
```

Never called.

**Cascade:** None.

---

### 6. `ElementBuffer.createDynamicShort(int)`

**File:** `core/src/main/java/hu/mudlee/core/render/ElementBuffer.java:16`

```java
public static ElementBuffer createDynamicShort(int maxShortCount) { ... }
```

No caller anywhere in core or sandbox.

**Cascade:** None.

---

### 7. `Texture2D.getNativeHandle()`

**File:** `core/src/main/java/hu/mudlee/core/render/texture/Texture2D.java:28`

```java
public abstract int getNativeHandle();
```

Declared in the abstract class and implemented in `VulkanTexture2D:82`, but never called
from any consumer code.

**Cascade:** Removing the declaration removes the override.

---

## API Surface Only (Low Priority)

These are all public API methods that the current sandbox does not call. They were
written for future engine consumers and are intentional. Review them to decide whether
to keep, shrink, or document as stable API.

### 8. `Animation2D.getFrameCount()`

**File:** `core/src/main/java/hu/mudlee/core/render/animation/Animation2D.java:52`

Introspection method. The renderer never queries frame count directly; it only calls
`getKeyFrame()`. Useful for debug overlays or tools, but currently dead.

**Cascade if removed:** None.

---

### 9. `Animation2D.getTotalDuration()`

**File:** `core/src/main/java/hu/mudlee/core/render/animation/Animation2D.java:56`

Same situation as `getFrameCount()`. Never called by the engine or sandbox.

**Cascade if removed:** None.

---

### 10. `AnimationPlayer2D.reset()`

**File:** `core/src/main/java/hu/mudlee/core/render/animation/AnimationPlayer2D.java:59`

Resets `stateTime` without changing the current animation. No caller exists. The only
current use case (replaying an animation from the start) is achieved by calling
`play(sameAnimation)` which switches and resets.

**Cascade if removed:** None.

---

### 11. `InputAction.onStarted(Consumer<InputActionContext>)`

**File:** `core/src/main/java/hu/mudlee/core/input/InputAction.java:80`

Registers a STARTED-phase callback. The sandbox only ever uses `onPerformed()`. The
internal `transitionTo()` machinery does fire STARTED events, so the infrastructure
works — it just has no registered listeners.

**Cascade if removed:** `startedCallbacks` list field on `InputAction` becomes unused.

---

### 12. `InputAction.onCanceled(Consumer<InputActionContext>)`

**File:** `core/src/main/java/hu/mudlee/core/input/InputAction.java:92`

Same as `onStarted()` — no sandbox code registers a canceled callback.

**Cascade if removed:** `canceledCallbacks` list field on `InputAction` becomes unused.

---

### 13. `InputAction.isEnabled()`

**File:** `core/src/main/java/hu/mudlee/core/input/InputAction.java:127`

The `enabled` field is maintained internally; `InputSystem` manages registration via
`enable()`/`disable()` and never queries `isEnabled()` back.

**Cascade if removed:** None.

---

### 14. `InputActionMap.findAction(String)`

**File:** `core/src/main/java/hu/mudlee/core/input/InputActionMap.java:57`

Lookup helper. Nobody queries actions by name at runtime in any current code path.

**Cascade if removed:** `InputAction.getName()` loses one of its two callers (the other
is `InputActionContext.actionName()`). If `actionName()` is also removed (see #17),
`getName()` becomes dead.

---

### 15. `InputActionMap.getActions()`

**File:** `core/src/main/java/hu/mudlee/core/input/InputActionMap.java:81`

Returns the unmodifiable action list. No caller.

**Cascade if removed:** None.

---

### 16. `InputActionMap.isEnabled()`

**File:** `core/src/main/java/hu/mudlee/core/input/InputActionMap.java:77`

No caller. The map tracks `enabled` for `enable()`/`disable()` but nobody reads the
flag back.

**Cascade if removed:** None.

---

### 17. `InputActionMap.getName()`

**File:** `core/src/main/java/hu/mudlee/core/input/InputActionMap.java:73`

No caller outside the class.

**Cascade if removed:** None.

---

### 18–22. `InputActionContext` read methods

**File:** `core/src/main/java/hu/mudlee/core/input/InputActionContext.java`

All five public methods on `InputActionContext` are never called in the current codebase.
The context object is passed to callbacks but no callback currently uses it (sandbox
uses `Keyboard.getState()` instead of the action system).

| Method | Line | Cascade if removed |
|--------|------|--------------------|
| `action()` | 32 | None |
| `phase()` | 37 | None |
| `actionName()` | 42 | `InputAction.getName()` loses its second caller |
| `readBoolean()` | 50 | `InputAction.isPressed()` becomes unused |
| `readFloat()` | 58 | `InputAction.readFloat()` loses its only caller → `InputSystem.readFloat()` may become unused |
| `readVector2()` | 66 | `InputAction.readVector2()` loses its only caller → `InputSystem.readVector2()` may become unused |

---

## Cascade Summary

If the `InputActionContext` read methods and map query methods are all deleted, the
following methods would also become orphaned:

- `InputAction.isPressed()` (only called by `InputActionContext.readBoolean()`)
- `InputAction.readFloat()` (only called by `InputActionContext.readFloat()`)
- `InputAction.readVector2()` (only called by `InputActionContext.readVector2()`)
- `InputAction.getName()` (only called by `InputActionContext.actionName()` and `InputActionMap.findAction()`)
- `InputSystem.readFloat(InputAction)` (only called by `InputAction.readFloat()`)
- `InputSystem.readVector2(InputAction)` (only called by `InputAction.readVector2()`)

---

## Summary Table

| # | Method | File | Category | Cascade? |
|---|--------|------|----------|----------|
| 1 | `Texture2D.unBind()` | `render/texture/Texture2D.java` | Truly dead | Removes 2 overrides |
| 2 | `VertexArray.setEBO(ElementBuffer)` | `render/VertexArray.java` | Truly dead | Removes 2 overrides |
| 3 | `VertexArray.setInstanceCount(int)` | `render/VertexArray.java` | Truly dead | Removes 2 overrides |
| 4 | `VertexBuffer.update(ByteBuffer, int)` | `render/VertexBuffer.java` | Truly dead | Removes 2 overrides |
| 5 | `ElementBuffer.update(ByteBuffer, int)` | `render/ElementBuffer.java` | Truly dead | Removes 1 override |
| 6 | `ElementBuffer.createDynamicShort(int)` | `render/ElementBuffer.java` | Truly dead | None |
| 7 | `Texture2D.getNativeHandle()` | `render/texture/Texture2D.java` | Truly dead | Removes 2 overrides |
| 8 | `Animation2D.getFrameCount()` | `render/animation/Animation2D.java` | API surface | None |
| 9 | `Animation2D.getTotalDuration()` | `render/animation/Animation2D.java` | API surface | None |
| 10 | `AnimationPlayer2D.reset()` | `render/animation/AnimationPlayer2D.java` | API surface | None |
| 11 | `InputAction.onStarted(Consumer)` | `input/InputAction.java` | API surface | Field unused |
| 12 | `InputAction.onCanceled(Consumer)` | `input/InputAction.java` | API surface | Field unused |
| 13 | `InputAction.isEnabled()` | `input/InputAction.java` | API surface | None |
| 14 | `InputActionMap.findAction(String)` | `input/InputActionMap.java` | API surface | May cascade |
| 15 | `InputActionMap.getActions()` | `input/InputActionMap.java` | API surface | None |
| 16 | `InputActionMap.isEnabled()` | `input/InputActionMap.java` | API surface | None |
| 17 | `InputActionMap.getName()` | `input/InputActionMap.java` | API surface | None |
| 18 | `InputActionContext.action()` | `input/InputActionContext.java` | API surface | None |
| 19 | `InputActionContext.phase()` | `input/InputActionContext.java` | API surface | None |
| 20 | `InputActionContext.actionName()` | `input/InputActionContext.java` | API surface | `InputAction.getName()` |
| 21 | `InputActionContext.readBoolean()` | `input/InputActionContext.java` | API surface | `InputAction.isPressed()` |
| 22 | `InputActionContext.readFloat()` | `input/InputActionContext.java` | API surface | Deep cascade |
| 23 | `InputActionContext.readVector2()` | `input/InputActionContext.java` | API surface | Deep cascade |
