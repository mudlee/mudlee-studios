# Plan 007 — Memory / GC Pressure Investigation

## Problem

The Vulkan backend shows ~1 MB/s heap growth as reported by the debug overlay
(`Runtime.getRuntime().totalMemory() - freeMemory()`). There are no true memory leaks
(no retained references that prevent GC), but sustained per-frame allocation creates GC
pressure that causes the JVM to grow its heap faster than the collector can reclaim it.
The growth will plateau and drop when a major GC fires, but until then it appears continuous.

---

## Findings (ordered by severity)

### 1 — `STBTTAlignedQuad.malloc()` inside `UIBatch.drawText()` ★★★
**File:** `core/.../ui/UIBatch.java`

`drawText()` allocates one `STBTTAlignedQuad` via `STBTTAlignedQuad.malloc()` and shares it
across 5 `drawTextRaw()` calls (4 outline shadows + 1 foreground). That is one native
malloc/free per `drawText()` invocation. With 8 `drawText()` calls per frame at 60 fps this
equals **480 native allocations per second**.

Because `-Dorg.lwjgl.util.Debug=true` is active, LWJGL wraps every `memAlloc` with a
Java heap tracking object that captures a stack trace — significantly amplifying per-alloc
overhead.

`BitmapFont.measureTextWidth()` has the same problem: it also calls `STBTTAlignedQuad.malloc()`
each invocation.

**Fix:** Allocate one `STBTTAlignedQuad` as a `private final` field on `UIBatch`, freed in
`dispose()`. Similarly, add a `private final STBTTAlignedQuad` field on `BitmapFont` for
`measureTextWidth()`.

**Status: FIXED**

---

### 2 — `new float[]{x}` cursor arrays inside `UIBatch.drawTextRaw()` ★★★
**File:** `core/.../ui/UIBatch.java`

`drawTextRaw()` creates two `float[1]` arrays on every call. With 5 calls per `drawText()`
and 8 `drawText()` invocations per frame: **80 heap array allocations per frame × 60 fps = 4,800/sec**.

`BitmapFont.measureTextWidth()` also allocates two `float[1]` arrays per call.

**Fix:** Two `private final float[1]` cursor fields on `UIBatch`, filled before each
`drawTextRaw()` call. Same approach on `BitmapFont` for `measureTextWidth()`.

**Status: FIXED**

---

### 3 — `stack.floats()` FloatBuffer wrappers inside `BitmapFont.getQuad()` ★★
**File:** `core/.../render/font/BitmapFont.java`

`stack.floats(f)` allocates a `FloatBuffer` Java wrapper object pointing into the
MemoryStack. The underlying native memory is stack-based (freed on pop), but the Java
wrapper lives on the heap until GC. `getQuad` is called once per character per pass:
~8 chars × 5 passes × 8 drawText = **~320 `FloatBuffer` objects per frame × 60 fps = 19,200/sec**.

**Fix:** Replace `stack.floats()` with two `private final FloatBuffer` fields allocated once
via `BufferUtils.createFloatBuffer(1)`. Fill them with `put(0, value)` before the STB call,
read back with `get(0)` after. Eliminates MemoryStack push/pop overhead entirely for this path.

**Status: FIXED**

---

### 4 — `Keyboard.getState()` / `Mouse.getState()` allocate per call ★
**File:** `core/.../input/InputSystem.java`

`getKeyboardState()` returns `new KeyboardState(KEY_STATE)` and `getMouseState()` returns
`new MouseState(...)` on every invocation. These are thin wrappers with no array copy, but
they are new heap objects each frame.

**Fix:** Maintain singleton `KeyboardState` / `MouseState` instances inside `InputSystem`;
update their fields in `InputSystem.update()`.

**Status: SKIPPED** — trivial allocations, low impact; GC handles them efficiently.

---

### 5 — `InputSystem.computeVector2()` allocates `new Vector2f` every call ★
**File:** `core/.../input/InputSystem.java`

Returns `new Vector2f(x, y)` unconditionally on every call.

**Fix:** Accept an output `Vector2f` parameter to fill in-place (matches JOML's own API style).

**Status: SKIPPED** — low impact; address only if profiler confirms it's significant.

---

### 6 — `String.format()` in `DebugStatsComponent.draw()` ★
**File:** `core/.../ui/DebugStatsComponent.java`

4 `String.format()` calls per frame inside `draw()` at 60 fps = ~240 `Formatter`/`String`
object pairs per second. Self-polluting: the monitor measuring heap growth is itself causing
heap growth.

**Fix:** Move formatting into `update()` and store the results in `String` fields. `draw()`
then uses the pre-computed strings with zero allocations.

**Status: FIXED**

---

### 7 — `MouseState.position()` allocates `new Vector2f` per call ★
**File:** `core/.../input/MouseState.java`

`position()` returns `new Vector2f(x, y)` every invocation.

**Fix:** Add `position(Vector2f out)` overload that writes into a caller-supplied instance.

**Status: SKIPPED** — not confirmed in hot path; address only if profiler shows it.

---

---

### 8 — `Color.toVector4f()` called every frame from `GraphicsDevice.clear()` ★★
**File:** `core/.../Color.java`, `core/.../GraphicsDevice.java`

```java
// GraphicsDevice.clear() — called once per frame in GameScene2D.draw()
Renderer.setClearColor(color.toVector4f());   // new Vector4f(r, g, b, a) every frame
```

`Color.toVector4f()` always constructs a new `Vector4f`. The clear color almost never
changes, yet a new `Vector4f` is allocated every frame.

**Fix:** Add `setClearColor(float r, float g, float b, float a)` directly on `Renderer` /
`GraphicsContext` so `GraphicsDevice.clear()` can bypass `toVector4f()`. Or add a
`private final Vector4f clearColorVec` field to `GraphicsDevice` and update it in-place
when the color changes.

**Status: FIXED**

---

### 9 — `GameScene2D.update()` / `draw()` for-each over `ArrayList<GameObject>` ★
**File:** `core/.../gameobject/GameScene2D.java`

Both `update()` (line 75) and `draw()` (line 84) use enhanced for-each on
`List<GameObject>`, allocating **2 `ArrayList$Itr` objects per frame**.

**Fix:** Index-based loops in both methods.

**Status: FIXED**

---

---

## Priority order for fixing

| # | Item                                                          | Estimated impact | Status  |
|---|---------------------------------------------------------------|------------------|---------|
| 1 | Reuse `STBTTAlignedQuad` field in `UIBatch`+`BitmapFont`      | High             | FIXED   |
| 2 | Reuse `float[1]` cursor fields in `UIBatch`+`BitmapFont`      | High             | FIXED   |
| 3 | Reuse `FloatBuffer` fields in `BitmapFont.getQuad`            | Medium           | FIXED   |
| 6 | Move `String.format` to `update()` in `DebugStats`            | Low              | FIXED   |
| 8 | Remove `Color.toVector4f()` from per-frame `clear()` path     | Medium           | FIXED   |
| 9 | Index-based loops in `GameScene2D.update()` / `draw()`        | Low              | FIXED   |
| 4 | Singleton `KeyboardState` / `MouseState`                      | Low              | SKIPPED |
| 5 | Reuse `Vector2f` in `computeVector2`                          | Low              | SKIPPED |
| 7 | `MouseState.position()` output overload                       | Low              | SKIPPED |

