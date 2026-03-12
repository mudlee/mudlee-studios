# Engine Audit Review

**Date:** 2026-03-11
**Reviewer:** Claude Opus 4.6 (Senior Java Architect / Graphics Engineering Specialist)
**Scope:** Independent review of `engine-audit.md` (2026-03-10), cross-referenced against the full codebase (115 Java source files in `core/`, 4 files in `sandbox/`).

---

## Executive Assessment

The audit is **solid work overall**. It correctly identifies the major performance bottlenecks (non-indexed SpriteBatch, GC pressure from allocations in hot paths, missing VMA) and provides actionable fixes with code samples. The "What Is Done Well" section (8) is accurate and shows the auditor understood idiomatic LWJGL patterns.

However, the audit contains **three factual errors**, **misses several real bugs**, and **underestimates the engine's existing diagnostic infrastructure**. The most significant miss is a render state tracking bug in `OpenGLGraphicsContext` that silently breaks polygon mode switching.

**Summary of this review:**
- 20 out of 27 findings: **Agree** (accurate and actionable)
- 3 findings: **Factually incorrect** (items 4.3, 9 roadmap "debug overlay", 10.2)
- 4 findings: **Partially correct** (minor accuracy issues)
- 8 additional findings the audit missed entirely
- 2 priority re-assessments

---

## Part 1: Findings Where I Agree

The following audit findings are **confirmed correct** after reading the relevant source files:

| # | Finding | Verdict |
|---|---------|---------|
| 1.1 | SpriteBatch2D non-indexed quads (6 verts/sprite) | **Correct.** `SpriteBatch2D.java:35` — `VERTICES_PER_SPRITE = 6`. Static EBO with (0,1,2, 0,2,3) pattern is the standard fix. |
| 1.2 | `begin()` allocates `Matrix4f` every frame | **Correct.** `SpriteBatch2D.java:75` — `new Matrix4f().setOrtho(...)` in the no-arg `begin()`. |
| 1.3 | OrthographicCamera2D no dirty flag | **Correct.** `OrthographicCamera2D.java:22-33` — five JOML operations unconditionally on every `getTransformMatrix()` call. |
| 1.5 | Blend state toggled unconditionally per flush | **Correct.** `SpriteBatch2D.java:244-247` — `setBlend(false)` after every flush. |
| 1.6 | Vulkan: No VMA | **Correct.** Direct `vkAllocateMemory` calls risk hitting `maxMemoryAllocationCount`. |
| 1.7 | Vulkan: Descriptor pool hard cap (256) | **Correct.** `VulkanContext.java:46` — `MAX_TEXTURE_DESCRIPTORS = 256`, no overflow pool. |
| 1.8 | Vulkan: vSync=false silent FIFO fallback | **Correct.** MAILBOX → FIFO without trying IMMEDIATE first. |
| 1.9 | OpenGL texture always bound to unit 0 | **Correct.** `OpenGLTexture2D.java:86` — hardcoded `GL_TEXTURE0`, confirmed by the existing TODO comment. |
| 2.1 | EntityManager cache cleared on every structural change | **Correct.** `EntityManager.java:33,39,51` — `queryCache.clear()` in `destroyEntity`, `addComponent`, `removeComponent`. |
| 2.2 | ComponentMapperService no caching | **Correct.** `ComponentMapperService.java:11` — `new ComponentMapper<>()` on every call. |
| 2.3 | Aspect no exclusion support | **Correct.** `Aspect.java` — only `all()`, no `exclude()`. |
| 2.4 | SpriteRender2DSystem comparator lambda per frame | **Correct.** `SpriteRender2DSystem.java:37` — `Comparator.comparingInt(lambda)` allocated every `render()`. |
| 2.5 | VelocityComponent dead code | **Correct.** No system in `core/` or `sandbox/` reads it. |
| 2.6 | `getEntitiesWith()` allocates HashSet per call | **Correct.** `EntityManager.java:72` — `new HashSet<>(Arrays.asList(required))` even on cache hits. |
| 2.7 | Entity IDs never recycled | **Correct.** `EntityManager.java:14,21` — monotonically increasing `nextId`. |
| 3.1 | Stream allocation on key/button release | **Correct.** `InputSystem.java:152,193,271` — `.stream().anyMatch(...)` on release paths. |
| 3.2 | `computeVector2()` allocates Vector2f per frame | **Correct.** `InputSystem.java:311,318,322` — `new Vector2f()` on every call. |
| 3.3 | `GamepadButton.values()` inside poll loop | **Correct.** `InputSystem.java:238` — `values()` returns a new array copy per JLS. |
| 4.2 | `Time.timeStarted` is public and mutable | **Correct.** `Time.java:4` — should be `private static final`. |
| 5.1 | ContentManager hardcoded to Texture2D | **Correct.** `ContentManager.java:39` — only one type in `resolve()`. |

---

## Part 2: Findings That Are Factually Incorrect

### INCORRECT: Item 4.3 — "No frame rate cap when VSync is disabled"

**Audit claims:** `Game.loop()` runs entirely unbounded when VSync is off.

**Reality:** `Game.java:116-124` has a sleep-based frame rate limiter:

```java
var elapsed = Time.getTime() - beginTime;
while (elapsed < TARGET_ELAPSED_SECONDS) {   // TARGET = 1/60
    try {
        Thread.sleep(1);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    elapsed = Time.getTime() - beginTime;
}
```

This spin-wait loop caps the frame rate at ~60 FPS regardless of VSync. The engine will **not** pin a CPU core at 100% or submit thousands of frames. This finding should be **removed entirely** from the audit.

**Note:** `Thread.sleep(1)` is not the most precise limiter (OS scheduler granularity), but it absolutely prevents unbounded spinning. A busier project could refine this to use `LockSupport.parkNanos()` for sub-millisecond accuracy, but this is a polish concern, not a missing feature.

---

### INCORRECT: Item 10.2 — "No SpriteBatch diagnostics"

**Audit claims:** No `renderCalls` or `maxSpritesInBatch` counters exist. "The only way to detect batching regressions is an external GPU profiler."

**Reality:** The engine already has comprehensive diagnostics:

- `Renderer.java:12-15` tracks `drawCallCount`, `vertexCount`, `textureCount`, `spriteBatchFlushCount`
- `SpriteBatch2D.flush()` calls `Renderer.incrementSpriteBatchFlushCount()` and `Renderer.incrementVertexCount()`
- `DebugStatsComponent.java` (372 lines) displays all these stats **in-engine** with colour-coded warning thresholds:
  - FPS (avg/min/max), frame time, heap %, allocation rate, GC pauses/sec, off-heap memory
  - Draw calls, SpriteBatch flushes, vertex count, texture count
  - GPU name, backend (OpenGL/Vulkan), window resolution, uptime
  - Toggle with F3

This is **more diagnostic infrastructure than LibGDX's SpriteBatch exposes**. The audit either didn't read `DebugStatsComponent.java` or missed `Renderer`'s static counters.

---

### INCORRECT: Section 9 Roadmap — "Debug / profiling overlay"

**Audit claims:** "DebugStatsComponent referenced but no GPU timing or draw call counter visible in-engine."

**Reality:** As described above, the `DebugStatsComponent` **is** a full debug overlay with draw call counters, flush counts, vertex counts, heap stats, GC pause tracking, and warning-level colour coding. The only valid sub-point is the absence of GPU timing queries (e.g., `GL_TIME_ELAPSED`), but calling the entire overlay "not visible in-engine" is wrong.

---

## Part 3: Findings That Are Partially Correct

### Item 4.1 — "Dead `deltaTime >= 0f` branch"

The branch analysis is correct (`0f >= 0f` is always true), but the audit's suggested fix (delta cap at 100ms to prevent spiral-of-death) should note that the existing sleep loop at lines 116-124 already provides an effective frame time floor. A max delta cap is still good practice, but the urgency is lower than implied.

---

### Item 5.2 — "String concatenation for cache key"

Correct observation, but the audit says "Since `load()` is typically called at scene load time (not in the hot loop)..." which is accurate. However, the code `type.getName() + ":" + assetName` only allocates one String object per call (JVM fuses the concatenation into a single `StringBuilder`). A record key would be cleaner but the performance difference is negligible.

---

### Item 7.1 — "BitmapFont not tracked by ContentManager"

Correct that BitmapFont bypasses ContentManager. However, the audit should also note that `BitmapFont` loads the TTF file **twice** during construction (`BitmapFont.java:51` for baking, `:78` for ascent metrics). This is a wasteful I/O pattern that should be fixed alongside the ContentManager integration.

---

### Item 10.4 — "OpenGL render state cache"

The audit correctly identifies missing state caching for blend calls but **misses that the polygon mode cache is already attempted and is broken** (see Part 4 below).

---

## Part 4: Findings the Audit Missed

### MISSED-1: Polygon mode tracking bug in OpenGLGraphicsContext (BUG)

**File:** `OpenGLGraphicsContext.java:27,93-95`

```java
private PolygonMode prevPolygonMode = PolygonMode.FILL;  // line 27

// In renderRaw():
if (prevPolygonMode != polygonMode) {                     // line 93
    glPolygonMode(GL41.GL_FRONT_AND_BACK, polygonMode.glRef);  // line 94
}                                                          // line 95
// prevPolygonMode is NEVER UPDATED
```

`prevPolygonMode` is initialized to `FILL` but **never assigned after construction**. This causes:

1. Switch FILL → WIREFRAME: condition true, GL call fires — **correct**
2. Switch WIREFRAME → WIREFRAME: condition true, redundant GL call — **harmless but wasteful**
3. Switch WIREFRAME → FILL: condition false (`FILL != FILL`), GL call **skipped** — **BUG: rendering stays in wireframe**

This is a real render state corruption bug. After using `PolygonMode.WIREFRAME` once, it becomes impossible to switch back to `FILL` without restarting. The fix is trivial:

```java
if (prevPolygonMode != polygonMode) {
    glPolygonMode(GL41.GL_FRONT_AND_BACK, polygonMode.glRef);
    prevPolygonMode = polygonMode;  // <-- add this
}
```

**Priority: High** — silent render state corruption.

---

### MISSED-2: OpenGLGraphicsContext issues one draw call per VBO (Architecture bug)

**File:** `OpenGLGraphicsContext.java:112-118`

```java
// Non-indexed, non-instanced path:
var vbos = vao.getVBOs();
for (int i = 0; i < vbos.size(); i++) {
    var vbo = vbos.get(i);
    glDrawArrays(renderMode.glRef, 0, vertexCount(vbo));
}
```

A VAO with multiple VBOs (e.g., separate position and colour streams) should issue **one** draw call, not one per VBO. The loop issues N draw calls for N VBOs, each drawing the full vertex count of that individual buffer.

Currently SpriteBatch2D uses a single VBO, so this doesn't manifest. But any future multi-stream vertex layout would break. The instanced path (`glDrawArraysInstanced`) has the same issue.

**Priority: Medium** — latent bug, not triggered by current usage.

---

### MISSED-3: `Window.getSize()` returns mutable internal state

**File:** `Window.java:66-68`

```java
public static Vector2i getSize() {
    return get().size;  // returns the actual internal field
}
```

External code can mutate the window's size tracking by modifying the returned `Vector2i`. Any call like `Window.getSize().set(0, 0)` would corrupt internal state. Should return a copy or an unmodifiable view.

**Priority: Medium** — defensive API issue.

---

### MISSED-4: No error handling around game loop

**File:** `Game.java:90-130`

```java
private void loop() {
    // ...
    while (!Window.shouldClose()) {
        // ... update, draw, etc
    }
}
// cleanup code follows in run()
```

If `update()` or `draw()` throws an exception, the cleanup code at `Game.java:58-66` (waitForGPU, unloadContent, dispose, Window.remove) is **never executed**. On Vulkan, this means GPU fences, command pools, and the swap chain leak. The loop should be wrapped in a try-finally:

```java
try {
    loop();
} finally {
    // cleanup
}
```

**Priority: Medium** — resource leak on crash.

---

### MISSED-5: Vulkan `setBlend()` and `setScissor()` are silently no-ops

**File:** `GraphicsContext.java:36-38`

```java
default void setBlend(boolean enable, BlendFactor src, BlendFactor dst) {}
default void setScissor(boolean enable, int x, int y, int width, int height) {}
```

`VulkanContext` does not override `setBlend()` or `setScissor()`. Since `SpriteBatch2D.flush()` calls `Renderer.setBlend(true, ...)` before every flush and `setBlend(false, ...)` after, these calls **silently do nothing on the Vulkan backend**.

For Vulkan, blending is configured as part of the pipeline state (not a dynamic call), so the OpenGL-style enable/disable model doesn't translate directly. However, this API mismatch should at minimum be documented or logged as a warning. Users switching from OpenGL to Vulkan will get different blending behaviour with no indication why.

**Priority: Low** — Vulkan blending currently works because the pipeline is configured with blending enabled. But the API contract is misleading.

---

### MISSED-6: `buildQuery()` creates new Entity objects for every cached result

**File:** `EntityManager.java:96-100`

```java
var result = new ArrayList<Entity>(ids.size());
for (var id : ids) {
    result.add(new Entity(id));  // new record instance per entity
}
return Collections.unmodifiableList(result);
```

Every time the query cache is rebuilt, `new Entity(id)` is called for each matching entity. Since `Entity` is a record, these are small allocations — but for particle-heavy scenes with frequent cache invalidation (see item 2.1), this multiplies the allocation count.

This is related to item 2.6 but is a separate allocation site. The fix is an entity lookup table or interned Entity instances.

**Priority: Low** — secondary to fixing cache invalidation (2.1).

---

### MISSED-7: `BitmapFont` loads the TTF file from disk twice

**File:** `BitmapFont.java:51,78`

```java
var ttfBytes = ResourceLoader.loadToDirectByteBuffer(ttfResourcePath);  // line 51 — for baking
// ...
var reloaded = ResourceLoader.loadToDirectByteBuffer(ttfResourcePath);  // line 78 — for metrics
```

The font file is loaded into a direct ByteBuffer, freed, and then loaded **again** just to extract vertical metrics. The `STBTTFontinfo` used for `stbtt_GetFontVMetrics` could be initialized from the same buffer before it's freed. This halves the I/O and native memory churn during font loading.

**Priority: Low** — construction-time only.

---

### MISSED-8: `ScreenManager` doesn't call `resize()` on newly shown screens

**File:** `ScreenManager.java:45-46,58-59`

```java
public void set(Screen screen) {
    // ... dispose old screens
    stack.push(screen);
    screen.show();   // no resize() call
}

public void push(Screen screen) {
    // ... hide previous
    stack.push(screen);
    screen.show();   // no resize() call
}
```

When a new screen enters the stack, it receives `show()` but not `resize(width, height)`. Screens that depend on `resize()` for initial layout (as is common in MonoGame's `Screen` pattern) won't know the current window dimensions until the next actual resize event. This can be worked around by querying `Window.getSize()` in `show()`, but the engine should call `resize()` automatically for consistency.

**Priority: Low** — easily worked around.

---

## Part 5: Priority Re-Assessments

### Upgrade: Item 1.9 (Texture unit 0) — Medium → Low

The audit rates this Medium ("blocks any future multi-texture work"), but the engine is a 2D sprite engine targeting MonoGame-style games. Multi-texture shaders (normal maps, lightmaps) are not on the near-term roadmap. This should be **Low** until 3D rendering or advanced 2D effects (e.g., lighting) are planned.

### Upgrade: MISSED-1 (Polygon mode bug) — Not in audit → **High**

This is a real correctness bug that silently prevents returning to `FILL` mode after using `WIREFRAME`. It affects anyone using debug wireframe rendering. Should be fixed immediately.

---

## Part 6: Structural & Scalability Assessment

The audit focuses primarily on performance micro-optimizations (GC pressure, allocation sites) and Vulkan readiness. These are valid, but I want to add some higher-level observations about engine structure and scalability.

### What the engine gets right architecturally

1. **Clean HAL via abstract factory pattern.** `Shader.create()`, `Texture2D.create()`, `VertexArray.create()` all route through `Renderer.activeBackend()`. Adding a new backend requires implementing concrete classes and adding a switch case — minimal surface area.

2. **MonoGame-aligned API.** `Game` → `initialize()` → `loadContent()` → `update()` / `draw()` → `unloadContent()` matches MonoGame's `Game` class lifecycle. The `Screen` / `ScreenManager` pattern (show/hide/dispose) mirrors MonoGame's `ScreenManager` from MonoGame.Extended. This alignment will make the engine intuitive for developers familiar with that ecosystem.

3. **Custom ECS with clean separation.** The decision to build a custom ECS (replacing Ashley) with `Aspect`, `ComponentMapper`, `EntityProcessingSystem`, and `RenderSystemBase` is a good long-term investment. The API is minimal but functional, and the `World` class provides a clean composition root.

4. **Input action system.** The three-layer input architecture (raw GLFW callbacks → static state arrays → `InputAction` with phases and composite bindings) is well-designed. The `ActionPhase` state machine (DISABLED → WAITING → STARTED → PERFORMED → CANCELED) correctly models Unity's `InputAction` lifecycle.

5. **Frame time and diagnostic infrastructure.** `GameTime` (elapsed + total + runningSlowly) combined with the comprehensive `DebugStatsComponent` gives developers visibility into engine health from day one.

### Scalability concerns not covered by the audit

1. **Single-World ECS.** The current design assumes one `World` per `Screen`. If the engine later needs overlapping worlds (e.g., a physics world + a render world), the tight coupling between `World`, `EntityManager`, and `SystemManager` would need refactoring. For now, this is fine.

2. **No system ordering guarantees.** `SystemManager` runs update systems in insertion order and render systems in insertion order. There's no explicit dependency declaration between systems. This works for small system counts but becomes fragile as the system count grows. Consider a `@After(OtherSystem.class)` annotation or explicit ordering API.

3. **Global static singletons.** `Window`, `Renderer`, `InputSystem`, `ScreenManager`, `Time` are all static singletons. This prevents:
   - Running two engine instances in the same JVM (e.g., for testing)
   - Mocking subsystems in unit tests
   - Headless mode for server-side simulation

   This is standard for game engines at this stage and is fine for now, but should be acknowledged as technical debt.

4. **No render pass abstraction.** The engine renders directly to the backbuffer. There's no `RenderTarget` or `Framebuffer` abstraction. This blocks:
   - Post-processing (bloom, colour grading, screen-space effects)
   - Shadow mapping
   - Multi-pass rendering
   - Render-to-texture for minimaps, portals, etc.

   This is the single most impactful missing abstraction for scaling beyond basic 2D rendering.

---

## Part 7: Revised Priority Summary

Combining the audit's findings (verified) with my additions, here is a corrected priority table:

| # | Issue | File(s) | Priority | Source |
|---|-------|---------|----------|--------|
| 1 | Vulkan: No VMA | `VulkanBuffer.java`, `VulkanTexture2D.java` | **High** | Audit 1.6 |
| 2 | SpriteBatch2D: non-indexed quads | `SpriteBatch2D.java` | **High** | Audit 1.1 |
| **3** | **Polygon mode never updated after GL call** | **`OpenGLGraphicsContext.java:27,93`** | **High** | **NEW** |
| 4 | Vulkan: descriptor pool hard cap | `VulkanContext.java:46` | **Medium** | Audit 1.7 |
| 5 | Vulkan: vSync=false fallback | `VulkanSwapChain.java` | **Medium** | Audit 1.8 |
| 6 | Blend state toggled unconditionally | `SpriteBatch2D.java:244` | **Medium** | Audit 1.5 |
| 7 | `begin()` allocates Matrix4f per frame | `SpriteBatch2D.java:75` | **Medium** | Audit 1.2 |
| 8 | Camera: no dirty flag | `OrthographicCamera2D.java` | **Medium** | Audit 1.3 |
| 9 | `computeVector2()` allocates Vector2f per frame | `InputSystem.java` | **Medium** | Audit 3.2 |
| 10 | Stream allocation on key/button release | `InputSystem.java` | **Medium** | Audit 3.1 |
| 11 | ECS cache cleared on every structural change | `EntityManager.java` | **Medium** | Audit 2.1 |
| 12 | `getEntitiesWith()` allocates HashSet per call | `EntityManager.java:72` | **Medium** | Audit 2.6 |
| 13 | ContentManager hardcoded to Texture2D | `ContentManager.java:39` | **Medium** | Audit 5.1 |
| 14 | Texture atlas missing | `SpriteBatch2D`, `ContentManager` | **Medium** | Audit 10.3 |
| 15 | BitmapFont not in ContentManager cache | `BitmapFont.java`, `UIService.java` | **Medium** | Audit 7.1 |
| **16** | **renderRaw() issues one draw call per VBO** | **`OpenGLGraphicsContext.java:112-118`** | **Medium** | **NEW** |
| **17** | **`Window.getSize()` returns mutable internal state** | **`Window.java:66-68`** | **Medium** | **NEW** |
| **18** | **No try-finally around game loop** | **`Game.java:56-66`** | **Medium** | **NEW** |
| 19 | OpenGL texture always bound to unit 0 | `OpenGLTexture2D.java:86` | **Low** | Audit 1.9 (downgraded) |
| 20 | `GamepadButton.values()` inside poll loop | `InputSystem.java:238` | **Low** | Audit 3.3 |
| 21 | `deltaTime >= 0f` always true | `Game.java:101` | **Low** | Audit 4.1 |
| 22 | `Time.timeStarted` is public and mutable | `Time.java:4` | **Low** | Audit 4.2 |
| 23 | ComponentMapperService does not cache mappers | `ComponentMapperService.java` | **Low** | Audit 2.2 |
| 24 | OpenGL render state cache (blend) | `OpenGLGraphicsContext.java` | **Low** | Audit 10.4 |
| 25 | SpriteRender2DSystem comparator lambda | `SpriteRender2DSystem.java:37` | **Low** | Audit 2.4 |
| 26 | VelocityComponent dead code | `VelocityComponent.java` | **Low** | Audit 2.5 |
| 27 | BitmapFont atlas not DPI-aware | `BitmapFont.java:54` | **Low** | Audit 7.2 |
| 28 | Entity IDs never recycled | `EntityManager.java:14` | **Low** | Audit 2.7 |
| 29 | GC collector not set (ZGC) | `sandbox/build.gradle` | **Low** | Audit 10.1 |
| **30** | **Vulkan setBlend/setScissor silently no-op** | **`GraphicsContext.java:36-38`** | **Low** | **NEW** |
| **31** | **BitmapFont loads TTF twice** | **`BitmapFont.java:51,78`** | **Low** | **NEW** |
| **32** | **ScreenManager doesn't resize new screens** | **`ScreenManager.java:45,58`** | **Low** | **NEW** |
| **33** | **buildQuery allocates Entity per result** | **`EntityManager.java:96-100`** | **Low** | **NEW** |
| ~~34~~ | ~~No frame rate cap when VSync disabled~~ | ~~`Game.java`~~ | ~~REMOVED~~ | ~~Audit 4.3 — INCORRECT~~ |
| ~~35~~ | ~~No SpriteBatch diagnostics~~ | ~~`SpriteBatch2D.java`~~ | ~~REMOVED~~ | ~~Audit 10.2 — INCORRECT~~ |

---

## Part 8: Conclusion

The original audit demonstrates strong technical depth, particularly in Vulkan best practices (VMA, descriptor pool overflow, present mode selection) and GC-aware Java patterns. Its priority ordering is mostly correct, and the "What Is Done Well" section properly credits the engine's solid foundations.

The main weaknesses of the audit are:
1. **Missed a real rendering bug** (polygon mode tracking) while finding mostly optimisation opportunities
2. **Failed to read `DebugStatsComponent.java`**, leading to two incorrect findings about missing diagnostics
3. **Missed the frame rate limiter** in the game loop, leading to the incorrect claim about unbounded CPU usage

The engine itself is in good shape for its stage of development. The recommended fix order is:
1. **Immediate:** Fix the polygon mode tracking bug (1-line fix)
2. **Next sprint:** Add indexed rendering to SpriteBatch2D, add try-finally around the game loop
3. **Before scaling:** Integrate VMA, make ContentManager extensible, add RenderTarget abstraction
4. **Ongoing:** Address GC pressure items incrementally as profiling confirms them
