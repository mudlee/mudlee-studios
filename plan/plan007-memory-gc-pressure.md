# Plan 007 — Memory / GC Pressure Investigation

## Problem

Both OpenGL and Vulkan backends show ~1 MB/s heap growth as reported by the debug overlay
(`Runtime.getRuntime().totalMemory() - freeMemory()`). There are no true memory leaks
(no retained references that prevent GC), but sustained per-frame allocation creates GC
pressure that causes the JVM to grow its heap faster than the collector can reclaim it.
The growth will plateau and drop when a major GC fires, but until then it appears continuous.

---

## Findings (ordered by severity)

### 1 — `STBTTAlignedQuad.malloc()` inside `UIBatch.drawTextRaw()` ★★★
**File:** `core/.../ui/UIBatch.java`

`drawText()` calls `drawTextRaw()` 5 times per string (4 outline shadows + 1 foreground).
With 8 `drawText` calls per frame that is **40 native malloc/free pairs per frame**.
At 60 fps this equals **2,400 native allocations per second**.

Because `-Dorg.lwjgl.util.Debug=true` is active, LWJGL wraps every `memAlloc` with a
Java heap tracking object that captures a stack trace — doubling the per-alloc overhead.

**Fix:** Allocate one `STBTTAlignedQuad` as a reusable `private final` field on `UIBatch`,
reset/reuse it instead of `malloc`-ing and freeing each call.

---

### 2 — `new float[]{x}` cursor arrays inside `UIBatch.drawTextRaw()` ★★★
**File:** `core/.../ui/UIBatch.java`

Same call site as above: two `float[1]` arrays are `new`-allocated per `drawTextRaw` call.
That is **80 heap array allocations per frame × 60 fps = 4,800/sec**.

**Fix:** Two `private final float[1]` cursor fields on `UIBatch`, zeroed/set before each call.

---

### 3 — `stack.floats()` FloatBuffer wrappers inside `BitmapFont.getQuad()` ★★
**File:** `core/.../render/font/BitmapFont.java`

`stack.floats(f)` allocates a `FloatBuffer` Java wrapper object pointing into the
MemoryStack. The underlying native memory is stack-based (freed on pop), but the Java
wrapper lives on the heap until GC. `getQuad` is called once per character per pass:
~8 chars × 40 passes = **~640 `FloatBuffer` objects per frame × 60 fps = 38,400/sec**.

**Fix:** Replace `stack.floats()` with direct MemoryStack address manipulation using
`stack.nfloat(value)` / `MemoryUtil.memGetFloat(ptr)` to avoid the heap wrapper entirely,
or restructure `getQuad` to accept the raw address directly.

---

### 4 — `Keyboard.getState()` / `Mouse.getState()` allocate per call ★
**File:** `core/.../input/InputSystem.java`

`getKeyboardState()` returns `new KeyboardState(KEY_STATE)` and `getMouseState()` returns
`new MouseState(...)` on every invocation. These are thin wrappers with no array copy, but
they are new heap objects each frame (and can be called multiple times per frame from
different components).

**Fix:** Maintain a single `static final KeyboardState` and `static final MouseState`
instance inside `InputSystem`; update their fields in `InputSystem.update()` instead of
constructing new ones. Expose mutably internally, read-only externally.

---

### 5 — `InputSystem.computeVector2()` allocates `new Vector2f` every call ★
**File:** `core/.../input/InputSystem.java`

Returns `new Vector2f(x, y)` (or `new Vector2f(0f, 0f)`) unconditionally on every call.
Called once per registered Vector2 action per frame.

**Fix:** Use a `private static final Vector2f` scratch result, or accept an output
`Vector2f` parameter to fill in-place.

---

### 6 — `String.format()` in `DebugStatsComponent.draw()` ★
**File:** `core/.../ui/DebugStatsComponent.java`

8 `String.format()` calls per frame at 60 fps = ~480 String objects/second.
Each `format` internally creates a `Formatter`, a `StringBuilder`, and the result `String`.

**Fix:** Pre-allocate a `StringBuilder`, format into it and call `.toString()` once, or
cache formatted strings and only reformat when the value actually changes.

---

### 7 — `MouseState.position()` allocates `new Vector2f` per call ★
**File:** `core/.../input/MouseState.java`

`position()` returns `new Vector2f(x, y)` every invocation. Not confirmed to be in the
hot path yet, but if game code calls it per frame the cost adds up.

**Fix:** Add `position(Vector2f out)` overload that writes into a caller-supplied instance,
or expose `x()` / `y()` only and remove the allocating helper.

---

## Priority order for fixing

| # | Item | Estimated impact |
|---|------|-----------------|
| 1 | Reuse `STBTTAlignedQuad` field in `UIBatch` | High |
| 2 | Reuse `float[1]` cursor fields in `UIBatch` | High |
| 3 | Remove `FloatBuffer` allocs in `BitmapFont.getQuad` | Medium |
| 4 | Singleton `KeyboardState` / `MouseState` | Low |
| 5 | Reuse `Vector2f` in `computeVector2` | Low |
| 6 | Avoid `String.format` every frame | Low |
| 7 | `MouseState.position()` output overload | Low |

Items 1 and 2 together should eliminate the majority of the observed growth.
