# Engine Audit

**Date:** 2026-03-10
**Codebase:** mudlee-studios — Java 2D/3D engine, LWJGL 3.4.0, OpenGL 4.1, Vulkan 1.3 (in progress)
**Auditor:** Lead Java Architect / Graphics Engineering Specialist

---

## Executive Summary

The engine has a solid architectural foundation: a clean HAL abstraction, a modern ECS with
ComponentMapper injection, a Unity-inspired input action system, and correct Vulkan
synchronization primitives — including fully wired factory methods routing to Vulkan
implementations and compiled SPIR-V shaders in place. The most pressing issues are
concentrated in three areas: **GC pressure in the hot loop** (Vector2f allocations, Stream
iterators, per-frame HashSet in `getEntitiesWith()`), **missing indexed rendering in
SpriteBatch2D** (50% excess vertex data), and **missing VMA usage in the Vulkan backend**
which risks hitting the driver's hard allocation limit at scale.

No issues were found that require architectural changes. Everything below is fixable
incrementally without breaking the existing API.

---

## 1. Rendering

### 1.1 SpriteBatch2D — Non-indexed quads

**File:** `core/.../render/SpriteBatch2D.java`

The batch uses 6 vertices per sprite (two raw triangles) instead of 4 vertices + an EBO
(element buffer). At `MAX_SPRITES = 1000`, the vertex buffer holds 54,000 floats
(216 KB). With indexed rendering it would be 36,000 floats (144 KB) — a 33% reduction in
per-flush GPU upload size.

```
Current:  6 verts × 9 floats × 1000 sprites = 54,000 floats
Indexed:  4 verts × 9 floats × 1000 sprites = 36,000 floats  (+EBO of 6 ints × 1000 = 24 KB)
```

An EBO for a static index pattern (0,1,2, 0,2,3 repeating) can be allocated once at
construction and never touched again. This is how LibGDX SpriteBatch and every other
production 2D batcher works.

**Priority:** High — affects every sprite draw call.

---

### 1.2 SpriteBatch2D — Matrix4f allocation in no-arg `begin()`

**File:** `SpriteBatch2D.java:75`

```java
public void begin() {
    var ortho = new Matrix4f().setOrtho(...);  // heap allocation every frame
    begin(ortho, identityMatrix);
}
```

`ortho` is a `new Matrix4f()` allocated on every call to the no-arg `begin()`. It should
be a cached field updated only when the window size changes (dirty flag on resize), or at
minimum a reused instance field recalculated in-place with `setOrtho`.

**Priority:** Medium — one Matrix4f per frame when no camera is supplied.

---

### 1.3 OrthographicCamera2D — No dirty flag

**File:** `core/.../render/camera/OrthographicCamera2D.java`

`getTransformMatrix()` fully recomputes the projection × view matrix on every call:
`setOrtho` → `translate` → `scale` → `rotateZ` → `translate`. This is five JOML
operations per frame even when the camera has not moved. The standard industry pattern
(used by LibGDX, MonoGame, Unity) is a dirty flag: mark the matrix stale when
`position`, `zoom`, `rotation`, or the window size changes; only recompute on the next
`getTransformMatrix()` call.

**Priority:** Medium — cheap operations, but unnecessary work on a static camera.

---

### ~~1.4 Vulkan backend not wired to factory layer~~ — **RETRACTED**

This finding was incorrect. All factory methods (`Shader.create()`, `Texture2D.create()`,
`VertexBuffer.create()`, `VertexArray.create()`, `Renderer`) correctly switch on the active
backend and instantiate the appropriate Vulkan implementation. The SPIR-V `.spv` files
(`vert.spv`, `frag.spv`) are also present under `resources/shaders/vulkan/2d/`.

The valid sub-concern (that `MemoryStack.stackPush()` in `renderRaw()` was wasteful) was
also incorrect — that is the idiomatic LWJGL pattern: a thread-local bump-pointer allocator
with zero GC overhead. See section 8 ("What Is Done Well") for both confirmations.

---

### 1.5 `Renderer.setBlend()` called unconditionally in `flush()`

**File:** `SpriteBatch2D.java:244–246`

```java
Renderer.setBlend(true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
Renderer.renderRaw(...);
Renderer.setBlend(false, SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
```

`setBlend(false, ...)` after every flush unconditionally disables blending. Any
transparent rendering outside the batch will silently fail to blend unless the caller
re-enables it. The engine should maintain a blend state cache and only issue the OpenGL
call when the state actually changes. Also, disabling blend after each flush is
unnecessary if the batch is the only renderer in the frame.

**Priority:** Medium.

---

### 1.6 Vulkan: No Vulkan Memory Allocator (VMA)

**Files:** `VulkanBuffer.java:50`, `VulkanTexture2D.java:210`

Both `VulkanBuffer` and `VulkanTexture2D` call `vkAllocateMemory` directly — one
device-memory allocation per buffer and per texture. The Vulkan spec does not
guarantee more than `maxMemoryAllocationCount` allocations exist simultaneously;
the value is driver-defined and is commonly **4096 on NVIDIA**, **1024 on some ARM
Mali** drivers. A scene with 200 sprites each on separate textures, plus their
dynamic vertex buffers (2 per frame × `FRAMES_IN_FLIGHT`), can approach this limit.

VMA (`org.lwjgl.util.vma`) is already bundled with LWJGL 3.4.0. It sub-allocates from
large driver-level blocks, reducing the actual `vkAllocateMemory` call count to O(heap
types), not O(resources). This is the industry standard approach — recommended by the
Vulkan spec itself and used by every production Vulkan engine.

```java
// Current (one OS allocation per buffer):
vkAllocateMemory(device.device(), allocInfo, null, pMemory);

// With VMA (one OS block, many sub-allocations):
vmaCreateBuffer(allocator, bufferInfo, vmaAllocInfo, pBuffer, pAllocation, null);
```

**Priority:** High — correctness risk at scale; VMA is already in the classpath.

---

### 1.7 Vulkan: Descriptor pool is a hard cap with no overflow handling

**File:** `VulkanContext.java:46`

```java
private static final int MAX_TEXTURE_DESCRIPTORS = 256;
```

The descriptor pool is allocated once with `maxSets = 256`. When a scene exceeds 256
unique bound textures, `vkAllocateDescriptorSets` returns
`VK_ERROR_OUT_OF_POOL_MEMORY`, which the engine wraps into an unchecked
`RuntimeException`. There is no pool growth, no secondary overflow pool, and no
graceful degradation.

The standard mitigation is a **pool-of-pools** pattern: allocate a new pool when the
current one is full, track pools in a list, and recycle drained pools. This is how
most production Vulkan engines handle dynamic descriptor allocation.

**Priority:** Medium — silent crash above 256 textures.

---

### 1.8 Vulkan: `vSync=false` silently falls back to VSync on Linux

**File:** `VulkanSwapChain.java:203–220`

```java
// When vSync is off, prefers MAILBOX but falls back to FIFO
for (int i = 0; i < count.get(0); i++) {
    if (modes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) { return MAILBOX; }
}
return VK_PRESENT_MODE_FIFO_KHR;  // ← effectively VSync=on
```

`VK_PRESENT_MODE_MAILBOX_KHR` is not guaranteed by the spec and is frequently absent
on Linux/X11 + Mesa (AMD and Intel) and on Wayland. The silent fallback to
`VK_PRESENT_MODE_FIFO_KHR` means `setVSync(false)` has no effect on those platforms.
`VK_PRESENT_MODE_IMMEDIATE_KHR` should be tried before FIFO, and a warning should be
logged on fallback:

```
// Preferred priority for vSync=false:
// MAILBOX → IMMEDIATE → FIFO (last resort, log a warning)
```

**Priority:** Medium — misleading behaviour on common Linux/Wayland setups.

---

### 1.9 OpenGL texture always bound to unit 0 — multi-texturing impossible

**File:** `OpenGLTexture2D.java:86`

```java
glActiveTexture(GL_TEXTURE0); // TODO: we should not use it here
glBindTexture(GL_TEXTURE_2D, textureId);
```

`bind()` hard-codes `GL_TEXTURE0`. The `Texture2D` interface has no `bind(int unit)`
overload, so the engine is structurally incapable of using more than one texture unit
at a time. This makes it impossible to implement multi-texture shaders (e.g., normal
maps + albedo, lightmaps, shadow atlas sampling) without first changing the API.

Additionally, `unBind()` only unbinds the texture object — it never resets the active
texture unit back to `GL_TEXTURE0`. Any code that calls `bind()` on unit N and then
assumes the active unit is still 0 will silently bind to the wrong unit.

The fix requires a signature change to the `Texture2D` / `GraphicsContext` abstraction:

```java
void bind(int unit);  // add to Texture2D interface
```

and updating `OpenGLTexture2D.bind(int unit)` to call
`glActiveTexture(GL_TEXTURE0 + unit)`.

**Priority:** Medium — blocks any future multi-texture work; the TODO confirms this is
a known debt.

---

## 2. ECS

### 2.1 EntityManager query cache — aggressive invalidation

**File:** `core/.../ecs/EntityManager.java`

`queryCache.clear()` is called on every `addComponent`, `removeComponent`, and
`destroyEntity`. This is correct for correctness but means that any frame where an entity
is created or destroyed causes every system's next `getEntitiesWith()` call to do a full
set-intersection rebuild. For games that spawn/despawn entities frequently (projectiles,
particles) this becomes a performance cliff.

A more robust strategy: version the cache with a `long dirtyToken`. Systems hold the
token they last queried against; if the current token differs, they rebuild. This is the
approach taken by Bevy's archetype system and most production ECS frameworks.

**Priority:** Medium — not a problem for the current demo, will matter at scale.

---

### 2.2 `ComponentMapperService.getMapper()` — no caching

**File:** `core/.../ecs/ComponentMapperService.java`

`getMapper(type)` creates a new `ComponentMapper` instance on every call. In
`initialize()`, each system calls this several times at startup — not in the hot loop —
so this is not a runtime cost. However, if `initialize()` is ever called more than once
(e.g., after world reload), duplicate mappers would exist. Caching by type in a
`HashMap` inside the service would be cleaner.

**Priority:** Low.

---

### 2.3 Aspect — `Class<?>[]` varargs, no exclusion support

**File:** `core/.../ecs/Aspect.java`

The `Aspect.all(...)` varargs parameter is stored as a raw array passed directly to
`getEntitiesWith()`. There is no support for exclusion predicates (e.g.,
`Aspect.all(A.class).exclude(B.class)`), which is a standard ECS feature. Without
exclusion, systems must check inside `process()` and early-return, polluting logic with
structural queries.

**Priority:** Low — add when a concrete use case arises.

---

### 2.4 `SpriteRender2DSystem` — Comparator lambda allocated per frame

**File:** `SpriteRender2DSystem.java:37`

```java
sortBuffer.sort(Comparator.comparingInt(e -> transformMapper.get(e).z));
```

`Comparator.comparingInt(lambda)` creates a new `Comparator` object wrapping a new
lambda instance on every call to `render()`. This is called once per frame. The fix is
a `private static final Comparator<Entity>` field — but since `transformMapper` is an
instance field it cannot be referenced from a static context. Instead use a stored
instance field initialised once:

```java
private Comparator<Entity> byZ;

@Override
public void initialize(ComponentMapperService mappers) {
    transformMapper = mappers.getMapper(Transform2DComponent.class);
    spriteMapper    = mappers.getMapper(Sprite2DComponent.class);
    byZ = Comparator.comparingInt(e -> transformMapper.get(e).z);
}
```

**Priority:** Low — one object per frame, but trivially fixed.

---

### 2.5 `VelocityComponent` is dead code

**File:** `core/ecs/component/VelocityComponent.java`

`VelocityComponent` defines a velocity vector but no system in `core` or `sandbox`
reads it. This misleads engine users into expecting a built-in movement system. Either
add a `MovementSystem` that applies velocity to `Transform2DComponent.position` each
frame, or remove the component until a concrete use case exists.

**Priority:** Low — no functional impact, but a documentation trap.

---

### 2.6 `getEntitiesWith()` allocates a `HashSet` on every call including cache hits

**File:** `EntityManager.java:72`

```java
public final List<Entity> getEntitiesWith(Class<? extends Component>... required) {
    var key = new HashSet<Class<? extends Component>>(Arrays.asList(required));  // ← every frame
    return queryCache.computeIfAbsent(key, k -> buildQuery(required));
}
```

`new HashSet<>(Arrays.asList(required))` is allocated on every call, even when the
result is already cached. `SpriteRender2DSystem` calls this once per frame. For N
systems each calling `getEntitiesWith()` once per frame at 60 fps: N × 60 HashSets +
N × 60 temporary Lists per second.

The standard fix is a two-component or variadic cache key that avoids heap allocation.
For the common 1–3 component case, a small value-type key is sufficient:

```java
// Cheapest option: sort the class array by identity hash and use Arrays as key
// Or use a EnumSet-style bitmask if component count is bounded
```

Alternatively, systems should hold a pre-built `Aspect` and the ECS layer should
supply a stable `EntityList` view per aspect (the Bevy/Flecs archetype model).

**Priority:** Medium — one HashSet per frame per system, adds up under profiling.

---

### 2.7 Entity IDs never recycled — unbounded counter

**File:** `EntityManager.java:14,21`

```java
private int nextId = 0;
// ...
var e = new Entity(nextId++);
```

Destroyed entities free their components but their integer IDs are never returned to a
free list. For games that create and destroy entities at high frequency (bullets,
particles, enemies), `nextId` grows monotonically until `Integer.MAX_VALUE` (~2.1
billion — unlikely to hit in practice) but more importantly, destroyed `byEntity` map
slots are removed yet the ID space is never compacted. Resurrection of a destroyed ID
is impossible without explicit handle tracking.

The standard fix is a `Deque<Integer> freeIds` stack: push IDs on `destroyEntity`,
pop from `freeIds` before incrementing `nextId` in `createEntity`. This is O(1) with
no additional GC pressure.

**Priority:** Low — not a practical concern for current scene sizes, but sets a bad
precedent for particle-heavy scenes.

---

## 3. Input System

### 3.1 Stream allocation on key/button release

**File:** `InputSystem.java:150–151, 191–192, 269–270`

```java
var boundToThisKey = action.bindings().stream()
        .anyMatch(b -> b instanceof InputBinding.KeyBinding kb && kb.key() == key);
```

`.stream().anyMatch(...)` allocates a `Stream` and a lambda instance on every key
release event. This is an event-driven path (not called every frame), but under rapid
input or many active actions it adds GC pressure. Replace with an explicit `for` loop:

```java
var boundToThisKey = false;
for (var b : action.bindings()) {
    if (b instanceof InputBinding.KeyBinding kb && kb.key() == key) {
        boundToThisKey = true;
        break;
    }
}
```

**Priority:** Medium.

---

### 3.2 Vector2f allocation in `computeVector2()`

**File:** `InputSystem.java:291–321`

`computeVector2()` returns `new Vector2f(x, y)` — potentially called every frame for
every VECTOR2 action. Since the engine is single-threaded, a single static reused
`Vector2f` field is sufficient:

```java
private static final Vector2f VECTOR2_RESULT = new Vector2f();

// return VECTOR2_RESULT.set(x, y) instead of new Vector2f(x, y)
```

**Priority:** Medium — one allocation per VECTOR2 action per frame.

---

### 3.3 `GamepadButton.values()` inside poll loop

**File:** `InputSystem.java:236`

```java
var button = GamepadButton.values()[i];  // inside a loop
```

`Enum.values()` returns a **new array copy** on every call in Java (it is not cached by
the JVM). This is inside the gamepad button poll loop (15 iterations per frame). Cache
it in a `private static final GamepadButton[] GAMEPAD_BUTTONS = GamepadButton.values()`
field.

**Priority:** Low — 15 iterations, but a trivially easy fix.

---

## 4. Game Loop & Core

### 4.1 `Game.loop()` — dead `deltaTime >= 0f` branch

**File:** `Game.java:101`

```java
var deltaTime = 0f;
// ...
if (deltaTime >= 0f) {  // always true: 0f >= 0f
```

This condition was presumably intended to skip the first frame or guard against negative
delta (impossible with `System.nanoTime()`). The branch is always taken. Either remove
it entirely or replace with a meaningful guard (e.g., skip frames where
`deltaTime > MAX_DELTA` to prevent the spiral-of-death):

```java
private static final float MAX_DELTA = 0.1f; // 100 ms cap

if (deltaTime < MAX_DELTA) { ... }
```

**Priority:** Low — no functional bug, but misleading code.

---

### 4.2 `Time` class — `timeStarted` is public

**File:** `Time.java:4`

```java
public static long timeStarted = System.nanoTime();
```

`timeStarted` is `public` and mutable. Any code can reset it, breaking all elapsed time
calculations. It should be `private static final`.

**Priority:** Low — internal class, but a correctness hazard.

---

### 4.3 No frame rate cap when VSync is disabled

**File:** `Game.java` (loop body)

When `GraphicsDeviceManager.setVSync(false)` is set (or Vulkan falls back to
`IMMEDIATE` mode), `Game.loop()` runs entirely unbounded — no sleep, no spin-wait,
no target frame time limit. On typical dev hardware this pins a CPU core at 100%,
submits thousands of redundant frames per second to the GPU, and causes thermal
throttling on laptops within minutes.

Add a minimum sleep at the end of the loop based on the remaining budget of
`TARGET_ELAPSED_SECONDS`:

```java
var sleepMs = (long) ((TARGET_ELAPSED_SECONDS - deltaTime) * 1000);
if (sleepMs > 1) { Thread.sleep(sleepMs); }
```

This is distinct from the delta cap in item 4.1 (which protects against too-large
deltas). That prevents the spiral-of-death; this prevents the opposite problem.

**Priority:** Low — power/thermal correctness, not a functional bug.

---

## 5. Content Management

### 5.1 `ContentManager` — hardcoded to `Texture2D`

**File:** `ContentManager.java:39–42`

```java
private <T> T resolve(Class<T> type, String assetName) {
    if (type == Texture2D.class) { ... }
    throw new IllegalArgumentException("Unsupported content type: " + type.getName());
}
```

Adding support for a new asset type (sound, font, shader) requires modifying this class.
The standard extensible pattern is a registry of `ContentLoader<T>` providers keyed by
`Class<T>`, registered at startup. This is how MonoGame's `ContentManager` and LibGDX's
`AssetManager` work.

**Priority:** Medium — will become a friction point as the engine grows.

---

### 5.2 `ContentManager.load()` — String concatenation for cache key

**File:** `ContentManager.java:19`

```java
var key = type.getName() + ":" + assetName;
```

A new `String` is allocated on every `load()` call, even for cache hits. Since `load()`
is typically called at scene load time (not in the hot loop), this is low impact. A
`record CacheKey(Class<?> type, String name)` would be slightly more efficient and
type-safe. Only fix if `load()` ever moves into the render loop.

**Priority:** Low.

---

## 6. UI System

### 6.1 UI layer uses SpriteBatch2D for font rendering

The `UIBatch` wraps `SpriteBatch2D`, which means UI text and sprites go through the
same batch and trigger flushes when the font texture differs from the sprite texture.
A dedicated UI pass with a separate `SpriteBatch2D` (and ideally, a pre-sorted draw
order by texture) would eliminate these mid-frame flushes.

**Priority:** Low — single texture per scene currently.

---

## 7. Font Rendering

### 7.1 `BitmapFont` not tracked by `ContentManager`

**Files:** `BitmapFont.java`, `UIService.java:37`, `ContentManager.java`

Every `new BitmapFont(path, ptSize)` allocates and uploads a 512×512 RGBA GPU
texture (~1 MB of VRAM). `ContentManager.load()` only handles `Texture2D`, so there
is no de-duplication. Two subsystems requesting the same font face at the same size
will each own a separate GPU texture. `UIService` constructs its `defaultFont` directly
rather than going through `ContentManager`.

This is the same extensibility problem as item 5.1 — the solution is the same:
register a `ContentLoader<BitmapFont>` keyed by `(path, ptSize)` in the loader
registry.

**Priority:** Medium — VRAM leak when fonts are shared across scenes.

---

### 7.2 `BitmapFont` atlas not DPI-aware

**File:** `BitmapFont.java:54`

```java
stbtt_BakeFontBitmap(ttfBytes, ptSize, bitmap, ATLAS_SIZE, ATLAS_SIZE, ...);
```

`stbtt_BakeFontBitmap` rasterises at logical pixel coordinates. The engine already
handles HiDPI display scaling via `ScreenPixelRatioHandler`, but the font atlas ignores
the pixel ratio. On a 2× Retina display, characters will be blurry because the
512×512 atlas was baked at half the physical resolution.

Fix: multiply `ptSize` by `Window.getPixelRatio()` during bake, and scale the quad
positions back when drawing. Alternatively, switch to SDF (Signed Distance Field)
rendering using `stbtt_PackFontRanges` with oversample, which scales cleanly to any
DPI with a single atlas.

**Priority:** Low — visual quality issue on HiDPI hardware.

---

## 8. What Is Done Well

The following are explicitly correct and should not be changed:

| Area                             | Detail                                                                                                                                 |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| SpriteBatch2D vertex writing     | Pre-allocated `float[]`, zero GC in hot path                                                                                           |
| TextureRegion                    | UV coordinates computed once at construction                                                                                           |
| AnimationPlayer2D                | Zero allocation per frame                                                                                                              |
| OpenGLShader uniform buffers     | `mat4Buf` / `vec4Buf` reused across calls                                                                                              |
| ResourceLoader                   | `MemoryStack` used correctly for STB image loading                                                                                     |
| InputBinding                     | Sealed interface + records — allocation-free pattern matching                                                                          |
| ECS Aspect + ComponentMapper     | Clean injection pattern, no EM in subclass constructors                                                                                |
| Vulkan sync                      | `FRAMES_IN_FLIGHT=2`, fences + semaphores correct                                                                                      |
| Vulkan push constants            | 128-byte matrices in `VK_SHADER_STAGE_VERTEX_BIT` — correct                                                                            |
| Vulkan `renderRaw()` stack usage | `MemoryStack.stackPush()` in `renderRaw()` is the idiomatic LWJGL pattern — pointer-bump on pre-allocated thread-local memory, zero GC |
| Vulkan pipeline                  | Lazy creation cached by `VertexBufferLayout` identity                                                                                  |
| `Disposable` interface           | Consistent explicit cleanup across all GPU resources                                                                                   |
| `InputActionMap`                 | Enable/disable grouping — correct Unity-style context pattern                                                                          |
| Gamepad deadzone                 | Applied per-axis at poll time, not per-read                                                                                            |

---

## 9. Missing Engine Features (Roadmap, Not Bugs)

These are not deficiencies in existing code but absent capabilities expected of a
production-grade 2D/3D engine at this stage:

| Feature                           | Notes                                                                                                                                                                                                                                                                                                                                                                                    |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Hierarchical transforms**       | `Transform2DComponent` has no parent entity reference. All positions are world-space absolute. Attaching a child entity to a parent (e.g., weapon to hand, UI widget to panel) requires the game to manually propagate transforms every frame. Standard fix: add an optional `parentEntity` field and a `TransformSystem` that computes world matrices from the parent chain each frame. |
| **3D rendering**                  | No mesh loading, no 3D camera, no PBR pipeline                                                                                                                                                                                                                                                                                                                                           |
| **Audio**                         | No sound system — OpenAL (via LWJGL) is the natural fit                                                                                                                                                                                                                                                                                                                                  |
| **Fixed timestep / physics step** | Game loop uses variable delta only; no fixed-step accumulator for deterministic simulation. The correct pattern (Gaffer on Games "Fix Your Timestep") uses an accumulator + delta cap (e.g., max 250 ms to prevent the spiral-of-death) + linear interpolation of state for rendering. Without it, physics and movement are frame-rate-dependent.                                        |
| **Asset pipeline**                | No asset packing, no hot reload, no async loading                                                                                                                                                                                                                                                                                                                                        |
| **Scene serialization**           | Scenes and entities are code-only                                                                                                                                                                                                                                                                                                                                                        |
| **Debug / profiling overlay**     | `DebugStatsComponent` referenced but no GPU timing or draw call counter visible in-engine                                                                                                                                                                                                                                                                                                |
| **ECS query exclusions**          | `Aspect.exclude(...)` not implemented                                                                                                                                                                                                                                                                                                                                                    |
| **Spatial partitioning**          | No quadtree or grid for physics/culling queries                                                                                                                                                                                                                                                                                                                                          |

---

## 10. JVM Configuration

### 10.1 Garbage collector selection

The default JVM GC (G1GC) produces pause times of 20–50 ms under moderate allocation
pressure — enough to cause visible frame hitches at 60 fps. For a game loop that must
sustain sub-16 ms frames, **ZGC** is the correct collector:

```
-XX:+UseZGC -Xmx512m -Xms256m
```

ZGC achieves 50 µs–1 ms pause times regardless of heap size by doing concurrent
compaction. The engine currently has no JVM flags set in `sandbox/build.gradle` beyond
the LWJGL requirements. Add ZGC to the JVM args block there.

The engine is single-threaded today so ZGC's parallel phase brings no throughput penalty.
If GC pause times are not yet causing frame drops this can be deferred, but it is a
zero-effort win once the allocation issues in sections 3.2 and 3.3 are fixed.

**Priority:** Low — set once, never revisit.

---

### 10.2 SpriteBatch diagnostics

LibGDX exposes `renderCalls` and `maxSpritesInBatch` public fields on its SpriteBatch.
These are frame-level diagnostics with zero runtime overhead (two integer increments).
Adding equivalent counters to `SpriteBatch2D` is the fastest way to detect:

- Excessive texture flushes (`renderCalls > 5` per frame with a single sprite type is a red flag)
- Batch size exhaustion (hitting `MAX_SPRITES = 1000` triggers a mid-batch flush that discards batching benefits)

Without these, the only way to detect batching regressions is an external GPU profiler.

**Priority:** Low — diagnostic only.

---

### 10.3 Texture atlas

Currently every `SpriteSheet2D` is a separate `Texture2D` and every distinct texture
triggers a flush in `SpriteBatch2D`. For a scene with more than a handful of distinct
sprite sheets, the flush count grows linearly with texture count. The standard fix is a
texture atlas: pack all sprites for a scene into one large texture, eliminating
mid-batch flushes from texture changes. This is how LibGDX's `TexturePacker` and
MonoGame's content pipeline work. Nothing in the current architecture prevents adding
this; it would be a new `TextureAtlas` class on top of the existing `Texture2D` /
`SpriteSheet2D` layer.

An advanced alternative is `GL_TEXTURE_2D_ARRAY` (available in OpenGL 3.0+): pack
same-size sprites as array layers and pass the layer index as a per-vertex attribute.
This avoids atlas packing entirely and has no bleed artifacts between sprites. The
trade-off is all layers must share the same resolution, which suits sprite sheets but
not mixed-size assets.

**Priority:** Medium — required before the engine can handle real scenes with mixed art.

---

### 10.4 OpenGL render state cache

The engine calls `glEnable`/`glDisable`, `glBlendFunc`, and similar state-setting
functions via `Renderer` and `OpenGLGraphicsContext` without tracking what is currently
set. Redundant state changes (setting blending to the same value it already has) are
no-ops on the GPU but still carry driver overhead. The standard fix is a thin shadow
state in `OpenGLGraphicsContext`:

```java
private boolean blendEnabled = false;
private BlendFactor srcFactor, dstFactor;

public void setBlend(boolean enabled, BlendFactor src, BlendFactor dst) {
    if (enabled == blendEnabled && src == srcFactor && dst == dstFactor) { return; }
    // issue GL calls
    blendEnabled = enabled; srcFactor = src; dstFactor = dst;
}
```

This is already the right pattern for `currentTexture` in `SpriteBatch2D` — the same
idea applied to the OpenGL state machine.

**Priority:** Low — relevant once scenes have mixed blend modes.

---

## 11. Priority Summary

| #   | Issue                                                                 | File                                        | Priority   |
|-----|-----------------------------------------------------------------------|---------------------------------------------|------------|
| 1   | Vulkan: No VMA — one `vkAllocateMemory` per resource                  | `VulkanBuffer.java`, `VulkanTexture2D.java` | **High**   |
| 2   | SpriteBatch2D: non-indexed quads                                      | `SpriteBatch2D.java`                        | **High**   |
| 3   | OpenGL texture always bound to unit 0 — blocks multi-texturing        | `OpenGLTexture2D.java:86`                   | **Medium** |
| 4   | Vulkan: descriptor pool hard cap, no overflow handling                | `VulkanContext.java:46`                     | **Medium** |
| 5   | Vulkan: `vSync=false` silently falls back to FIFO on Linux            | `VulkanSwapChain.java:203`                  | **Medium** |
| 6   | Blend state toggled unconditionally per flush                         | `SpriteBatch2D.java:244`                    | **Medium** |
| 7   | `begin()` allocates `Matrix4f` every frame                            | `SpriteBatch2D.java:75`                     | **Medium** |
| 8   | Camera: no dirty flag                                                 | `OrthographicCamera2D.java`                 | **Medium** |
| 9   | `computeVector2()` allocates `Vector2f` per frame                     | `InputSystem.java:309,316,320`              | **Medium** |
| 10  | Stream allocation on key/button release                               | `InputSystem.java:150,191,269`              | **Medium** |
| 11  | ECS cache cleared on every structural change                          | `EntityManager.java:33,39,51`               | **Medium** |
| 12  | `getEntitiesWith()` allocates `HashSet` per call including cache hits | `EntityManager.java:72`                     | **Medium** |
| 13  | `ContentManager` hardcoded to `Texture2D`                             | `ContentManager.java:39`                    | **Medium** |
| 14  | Texture atlas missing — each sheet triggers a flush                   | `SpriteBatch2D`, `ContentManager`           | **Medium** |
| 15  | `BitmapFont` not in `ContentManager` cache                            | `BitmapFont.java`, `UIService.java`         | **Medium** |
| 16  | `GamepadButton.values()` inside poll loop                             | `InputSystem.java:236`                      | **Low**    |
| 17  | `deltaTime >= 0f` always true — no delta cap                          | `Game.java:101`                             | **Low**    |
| 18  | `Time.timeStarted` is public and mutable                              | `Time.java:4`                               | **Low**    |
| 19  | `ComponentMapperService` does not cache mappers                       | `ComponentMapperService.java`               | **Low**    |
| 20  | No OpenGL render state cache — redundant driver calls                 | `OpenGLGraphicsContext.java`                | **Low**    |
| 21  | No SpriteBatch diagnostics (`renderCalls` counter)                    | `SpriteBatch2D.java`                        | **Low**    |
| 22  | GC collector not set — G1GC default causes 20+ ms pauses              | `sandbox/build.gradle` (JVM args)           | **Low**    |
| 23  | `SpriteRender2DSystem`: Comparator lambda per frame                   | `SpriteRender2DSystem.java:37`              | **Low**    |
| 24  | `VelocityComponent` is dead code — no consuming system                | `VelocityComponent.java`                    | **Low**    |
| 25  | `BitmapFont` atlas not DPI-aware — blurry on HiDPI                    | `BitmapFont.java:54`                        | **Low**    |
| 26  | No frame rate cap when VSync disabled                                 | `Game.java` (loop)                          | **Low**    |
| 27  | Entity IDs never recycled — unbounded counter                         | `EntityManager.java:14`                     | **Low**    |
