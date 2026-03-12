# Engine Audit — Final Consolidated Report

**Date:** 2026-03-11  
**Sources:** Original audit (2026-03-10), Claude review (2026-03-11), GPT review (2026-03-11)

---

## Executive Summary

This document consolidates findings from the engine audit and two independent reviews. Findings marked as incorrect, speculative, or false by either reviewer have been removed. New findings from the reviews have been added. All action items are numbered and prioritized (highest priority first).

The engine has a solid architectural foundation: clean HAL abstraction, modern ECS with ComponentMapper injection, Unity-inspired input action system, and correct Vulkan synchronization primitives. The most pressing issues are:

- **Correctness bugs** (polygon mode tracking, resource cleanup on crash)
- **Scalability risks** (Vulkan memory allocation, descriptor pool limits)
- **Performance bottlenecks** (non-indexed SpriteBatch, GC pressure in hot paths)
- **Structural concerns** (hierarchical transforms, render abstraction thinness, service ordering)

---

## Action Items

### Critical / High Priority

---

#### 1. Polygon mode never updated after GL call (BUG) - DONE

**File:** `OpenGLGraphicsContext.java:27,93-95`

**Problem:** `prevPolygonMode` is initialized to `FILL` but never assigned after the GL call fires. After using `PolygonMode.WIREFRAME` once, it becomes impossible to switch back to `FILL` without restarting the application.

**Why necessary:** This is a silent render state corruption bug. Anyone using debug wireframe rendering will find their application stuck in wireframe mode. A 1-line fix restores correctness.

---

#### 2. Vulkan: No Vulkan Memory Allocator (VMA) - DONE

**Files:** `VulkanBuffer.java:50`, `VulkanTexture2D.java:210`

**Problem:** Both classes call `vkAllocateMemory` directly — one device-memory allocation per buffer and per texture. The Vulkan spec limits simultaneous allocations to `maxMemoryAllocationCount` (commonly 4096 on NVIDIA, 1024 on ARM Mali).

**Why necessary:** A scene with 200+ textures and their dynamic vertex buffers can approach this hard driver limit, causing crashes. VMA (already in LWJGL 3.4.0 classpath) sub-allocates from large blocks, reducing actual `vkAllocateMemory` calls to O(heap types). This is the industry standard approach recommended by the Vulkan spec.

---

#### 3. SpriteBatch2D: Non-indexed quads - DONE

**File:** `SpriteBatch2D.java`

**Problem:** The batch uses 6 vertices per sprite (two raw triangles) instead of 4 vertices + an EBO. At `MAX_SPRITES = 1000`, the vertex buffer holds 54,000 floats (216 KB). With indexed rendering it would be 36,000 floats (144 KB) — a 33% reduction in per-flush GPU upload size.

**Why necessary:** This affects every sprite draw call. An EBO for the static index pattern (0,1,2, 0,2,3 repeating) can be allocated once at construction and never touched again. This is how LibGDX SpriteBatch and every production 2D batcher works.

---

#### 4. Hierarchical transforms missing - SKIPPED

**File:** `Transform2DComponent.java`

**Problem:** `Transform2DComponent` has no parent entity reference. All positions are world-space absolute. Attaching a child entity to a parent (weapon to hand, UI widget to panel) requires manual transform propagation every frame.

**Why necessary:** This directly affects scene scalability and engine usability. Weapons, UI panels, camera rigs, attachments, grouped animations, and prefab composition all become manual. Standard fix: add an optional `parentEntity` field and a `TransformSystem` that computes world matrices from the parent chain.

---

### Medium Priority

---

#### 5. No try-finally around game loop - DONE

**File:** `Game.java:90-130`

**Problem:** If `update()` or `draw()` throws an exception, cleanup code (`waitForGPU`, `unloadContent`, `dispose`, `Window.remove`) is never executed. On Vulkan, GPU fences, command pools, and swap chains leak.

**Why necessary:** Resource leaks on crash are difficult to debug and can leave the GPU in a bad state. Wrapping the loop in try-finally ensures cleanup runs regardless of exceptions.

---

#### 6. Vulkan: Descriptor pool hard cap (256)

**File:** `VulkanContext.java:46`

**Problem:** The descriptor pool is allocated once with `maxSets = 256`. When a scene exceeds 256 unique bound textures, `vkAllocateDescriptorSets` returns `VK_ERROR_OUT_OF_POOL_MEMORY`, causing an unchecked exception. No pool growth, no overflow pool, no graceful degradation.

**Why necessary:** Silent crash above 256 textures. The standard mitigation is a pool-of-pools pattern: allocate a new pool when current is full, track pools in a list, recycle drained pools.

---

#### 7. Vulkan: `vSync=false` silently falls back to FIFO on Linux

**File:** `VulkanSwapChain.java:203-220`

**Problem:** `VK_PRESENT_MODE_MAILBOX_KHR` is frequently absent on Linux/X11 + Mesa. The silent fallback to `FIFO` means `setVSync(false)` has no effect on those platforms.

**Why necessary:** Misleading behaviour on common Linux setups. Should try `IMMEDIATE` before falling back to `FIFO`, and log a warning on fallback.

---

#### 8. Blend state toggled unconditionally per flush - DONE

**File:** `SpriteBatch2D.java:244-246`

**Problem:** `setBlend(false, ...)` after every flush unconditionally disables blending. Any transparent rendering outside the batch will silently fail to blend unless the caller re-enables it.

**Why necessary:** The engine should maintain a blend state cache and only issue the GL call when state actually changes. Also, disabling blend after each flush is unnecessary if the batch is the only renderer.

---

#### 9. `begin()` allocates `Matrix4f` every frame - DONE

**File:** `SpriteBatch2D.java:75`

**Problem:** The no-arg `begin()` creates `new Matrix4f().setOrtho(...)` on every call — a heap allocation every frame when no camera is supplied.

**Why necessary:** Should be a cached field updated only on window resize, or a reused instance field recalculated in-place.

---

#### 10. OrthographicCamera2D: No dirty flag - DONE

**File:** `OrthographicCamera2D.java`

**Problem:** `getTransformMatrix()` fully recomputes projection × view matrix on every call (5 JOML operations) even when camera hasn't moved.

**Why necessary:** Unnecessary work on a static camera. The standard pattern (LibGDX, MonoGame, Unity) is a dirty flag: only recompute when position, zoom, rotation, or window size changes.

---

#### 11. `computeVector2()` allocates `Vector2f` per frame - DONE

**File:** `InputSystem.java:291-321`

**Problem:** Returns `new Vector2f(x, y)` — potentially called every frame for every VECTOR2 action.

**Why necessary:** GC pressure in the hot loop. Since the engine is single-threaded, a static reused `Vector2f` field is sufficient.

---

#### 12. Stream allocation on key/button release - DONE

**File:** `InputSystem.java:150-151, 191-192, 269-270`

**Problem:** `.stream().anyMatch(...)` allocates a Stream and lambda instance on every key release event.

**Why necessary:** Under rapid input this adds GC pressure. Replace with an explicit for-loop.

---

#### 13. ECS cache cleared on every structural change - DONE

**File:** `EntityManager.java`

**Problem:** `queryCache.clear()` is called on every `addComponent`, `removeComponent`, and `destroyEntity`. Any frame with entity creation/destruction causes full set-intersection rebuild for all systems.

**Why necessary:** Performance cliff for games with frequent spawning/despawning (projectiles, particles). A versioned cache with dirty tokens is more robust.

---

#### 14. `getEntitiesWith()` allocates `HashSet` per call - DONE

**File:** `EntityManager.java:72`

**Problem:** `new HashSet<>(Arrays.asList(required))` allocated on every call, even for cache hits.

**Why necessary:** For N systems each calling `getEntitiesWith()` once per frame at 60 fps: N × 60 HashSets per second. Use a value-type cache key or pre-built Aspect per system.

---

#### 15. `ContentManager` hardcoded to `Texture2D` - DONE

**File:** `ContentManager.java:39-42`

**Problem:** Adding support for new asset types (sound, font, shader) requires modifying this class.

**Why necessary:** Friction point as the engine grows. The standard pattern is a registry of `ContentLoader<T>` providers keyed by `Class<T>`, as in MonoGame's ContentManager and LibGDX's AssetManager.

---

#### 16. Texture atlas missing - DONE

**Files:** `SpriteBatch2D`, `ContentManager`

**Problem:** Every `SpriteSheet2D` is a separate `Texture2D`. Each distinct texture triggers a flush. Flush count grows linearly with texture count.

**Why necessary:** Required before the engine can handle real scenes with mixed art. Standard fix: pack all sprites into one atlas, eliminating mid-batch flushes from texture changes.

---

#### 17. `BitmapFont` not tracked by `ContentManager` - DONE

**Files:** `BitmapFont.java`, `UIService.java:37`

**Problem:** Every `new BitmapFont(path, ptSize)` allocates a 512×512 RGBA GPU texture (~1 MB VRAM). No de-duplication. Two subsystems requesting the same font get separate GPU textures.

**Why necessary:** VRAM leak when fonts are shared across scenes. Register a `ContentLoader<BitmapFont>` in the loader registry.

---

#### 18. `renderRaw()` issues one draw call per VBO - DONE

**File:** `OpenGLGraphicsContext.java:112-118`

**Problem:** A VAO with multiple VBOs should issue one draw call, not one per VBO. The loop issues N draw calls for N VBOs.

**Why necessary:** Latent architecture bug. Currently SpriteBatch2D uses a single VBO, but any future multi-stream vertex layout would break.

---

#### 19. `Window.getSize()` returns mutable internal state - DONE

**File:** `Window.java:66-68`

**Problem:** External code can mutate the window's size tracking by modifying the returned `Vector2i`.

**Why necessary:** Defensive API issue. Should return a copy or unmodifiable view.

---

#### 20. Service ordering is order-sensitive and loosely encapsulated - SKIPPED

**File:** `Game.java:23, 105-110`

**Problem:** `Game` exposes a public mutable `components` list. Update and draw execute in insertion order. The sandbox already depends on this ordering explicitly.

**Why necessary:** As service count grows, ordering becomes convention-driven instead of engine-enforced. Consider explicit dependency declarations.

---

#### 21. Render abstraction is too thin for engine growth - SKIPPED

**Files:** `RenderContext.java:20`, `SpriteRender2DSystem.java:30-33`

**Problem:** `RenderContext` is a marker interface. Render systems must cast it back to `SpriteBatch2D`, meaning they're not truly renderer-agnostic.

**Why necessary:** Structural scalability concern. If the engine grows into multiple render passes, 3D, deferred paths, or richer UI pipeline, this contract will need to evolve.

---

#### 22. Timing precision uses float for absolute time - DONE

**Files:** `Time.java:3-8`, `Game.java:91-127`, `GameTime.java:11-39`

**Problem:** The engine measures absolute time as `float` seconds. This loses precision over long sessions, and `deltaTime` is derived from float timestamp differences.

**Why necessary:** Long-run stability issue. Keep internal timing in `long` nanoseconds or `double`, even if public API remains float-based.

---

#### 23. Module encapsulation exports Vulkan internals - DONE

**File:** `module-info.java:30`

**Problem:** Exports the Vulkan implementation package directly, allowing engine consumers to couple themselves to Vulkan internals.

**Why necessary:** Weakens the otherwise good backend abstraction. Consumer code should only use the public HAL interfaces.

---

### Low Priority

---

#### 24. OpenGL texture always bound to unit 0

**File:** `OpenGLTexture2D.java:86`

**Problem:** `bind()` hard-codes `GL_TEXTURE0`. The `Texture2D` interface has no `bind(int unit)` overload.

**Why necessary:** Blocks future multi-texture work (normal maps, lightmaps). Lower priority for a 2D sprite engine until 3D or advanced effects are planned.

---

#### 25. `GamepadButton.values()` inside poll loop - DONE

**File:** `InputSystem.java:236`

**Problem:** `Enum.values()` returns a new array copy on every call (15 iterations per frame).

**Why necessary:** Trivially easy fix: cache in a static final field.

---

#### 26. `deltaTime >= 0f` always true — no delta cap

**File:** `Game.java:101`

**Problem:** Branch is always taken. Should either remove or replace with a meaningful max delta guard.

**Why necessary:** Misleading code. A delta cap (e.g., 100ms) prevents the spiral-of-death.

---

#### 27. `Time.timeStarted` is public and mutable

**File:** `Time.java:4`

**Problem:** Any code can reset it, breaking all elapsed time calculations.

**Why necessary:** Should be `private static final`. Correctness hazard.

---

#### 28. `ComponentMapperService` does not cache mappers

**File:** `ComponentMapperService.java`

**Problem:** `getMapper(type)` creates a new `ComponentMapper` instance on every call.

**Why necessary:** Not a runtime cost (only called at startup), but caching by type would be cleaner.

---

#### 29. OpenGL render state cache (blend)

**File:** `OpenGLGraphicsContext.java`

**Problem:** Redundant state changes (setting blending to the same value) carry driver overhead.

**Why necessary:** Standard fix is a thin shadow state. Relevant once scenes have mixed blend modes.

---

#### 30. `SpriteRender2DSystem` comparator lambda per frame

**File:** `SpriteRender2DSystem.java:37`

**Problem:** `Comparator.comparingInt(lambda)` creates new objects on every `render()` call.

**Why necessary:** One object per frame, but trivially fixed with a cached instance field.

---

#### 31. `VelocityComponent` is dead code

**File:** `VelocityComponent.java`

**Problem:** No system reads it. Misleads users into expecting a built-in movement system.

**Why necessary:** Either add a `MovementSystem` or remove until a use case exists.

---

#### 32. `BitmapFont` atlas not DPI-aware

**File:** `BitmapFont.java:54`

**Problem:** Font atlas ignores pixel ratio. On 2× Retina displays, characters are blurry.

**Why necessary:** Visual quality issue on HiDPI hardware. Multiply `ptSize` by pixel ratio during bake, or switch to SDF rendering.

---

#### 33. Entity IDs never recycled

**File:** `EntityManager.java:14,21`

**Problem:** Destroyed entity IDs are never returned to a free list.

**Why necessary:** Not a practical concern for current scene sizes, but sets a bad precedent for particle-heavy scenes. Standard fix: a `Deque<Integer>` free ID stack.

---

#### 34. Vulkan `setBlend()` and `setScissor()` are silently no-ops

**File:** `GraphicsContext.java:36-38`

**Problem:** `VulkanContext` doesn't override these methods. Calls from `SpriteBatch2D.flush()` do nothing on Vulkan backend.

**Why necessary:** API mismatch. Users switching from OpenGL to Vulkan get different blending behaviour with no indication. Should log a warning or document the limitation.

---

#### 35. `BitmapFont` loads TTF file twice

**File:** `BitmapFont.java:51,78`

**Problem:** Font file loaded into direct ByteBuffer, freed, then loaded again for metrics.

**Why necessary:** Wasteful I/O and native memory churn. Initialize `STBTTFontinfo` from the same buffer before freeing.

---

#### 36. `ScreenManager` doesn't call `resize()` on newly shown screens

**File:** `ScreenManager.java:45-46,58-59`

**Problem:** New screens receive `show()` but not `resize(width, height)`. Screens depending on `resize()` for initial layout won't know window dimensions until next resize event.

**Why necessary:** Easily worked around, but the engine should call `resize()` automatically for consistency with MonoGame's Screen pattern.

---

#### 37. `buildQuery()` creates new Entity objects for every cached result

**File:** `EntityManager.java:96-100`

**Problem:** Every cache rebuild calls `new Entity(id)` for each matching entity.

**Why necessary:** Secondary to fixing cache invalidation (#13). Multiplies allocation count in particle-heavy scenes.

---

#### 38. Global static singletons limit flexibility

**Files:** `Window`, `Renderer`, `InputSystem`, `ScreenManager`, `Time`

**Problem:** Static singletons prevent running two engine instances in the same JVM, mocking subsystems in tests, or headless server-side simulation.

**Why necessary:** Standard for game engines at this stage, but should be acknowledged as technical debt.

---

#### 39. No render pass abstraction

**Problem:** The engine renders directly to the backbuffer. No `RenderTarget` or `Framebuffer` abstraction.

**Why necessary:** Blocks post-processing (bloom, colour grading), shadow mapping, multi-pass rendering, and render-to-texture for minimaps/portals. Most impactful missing abstraction for scaling beyond basic 2D.

---

## Removed Findings

The following findings from the original audit were marked as incorrect by one or both reviewers and have been excluded:

| Finding | Reason for Removal |
|---------|-------------------|
| "No frame rate cap when VSync is disabled" | **Incorrect.** `Game.java:116-124` has a sleep-based frame rate limiter that caps at ~60 FPS. |
| "No SpriteBatch diagnostics" | **Incorrect.** `Renderer.java` tracks `drawCallCount`, `vertexCount`, `textureCount`, `spriteBatchFlushCount`. `DebugStatsComponent.java` (372 lines) displays comprehensive stats with F3 toggle. |
| "Debug / profiling overlay not visible in-engine" | **Incorrect.** `DebugStatsComponent` is a full debug overlay with draw call counters, flush counts, heap stats, GC tracking, and warning-level colour coding. |
| JVM/GC tuning advice (hard claims about G1GC pauses) | **Speculative.** No profiling evidence that the engine suffers measurable GC pauses. Keep "profile before tuning" as advice, but remove specific collector recommendations from action items. |
| Micro-optimizations (ComponentMapperService caching at startup, entity ID recycling urgency) | **De-prioritized.** Real but too small compared to structural and scalability items. Moved to low priority. |

---

## What Is Done Well

The following are explicitly correct and should not be changed:

| Area | Detail |
|------|--------|
| SpriteBatch2D vertex writing | Pre-allocated `float[]`, zero GC in hot path |
| TextureRegion | UV coordinates computed once at construction |
| AnimationPlayer2D | Zero allocation per frame |
| OpenGLShader uniform buffers | `mat4Buf` / `vec4Buf` reused across calls |
| ResourceLoader | `MemoryStack` used correctly for STB image loading |
| InputBinding | Sealed interface + records — allocation-free pattern matching |
| ECS Aspect + ComponentMapper | Clean injection pattern |
| Vulkan sync | `FRAMES_IN_FLIGHT=2`, fences + semaphores correct |
| Vulkan push constants | 128-byte matrices in `VK_SHADER_STAGE_VERTEX_BIT` — correct |
| Vulkan `renderRaw()` stack usage | `MemoryStack.stackPush()` is idiomatic LWJGL — zero GC |
| Vulkan pipeline | Lazy creation cached by `VertexBufferLayout` identity |
| `Disposable` interface | Consistent explicit cleanup across all GPU resources |
| `InputActionMap` | Enable/disable grouping — correct Unity-style context pattern |
| Gamepad deadzone | Applied per-axis at poll time |
| HAL via abstract factory | `Shader.create()`, `Texture2D.create()` route through `Renderer.activeBackend()` |
| MonoGame-aligned API | Game lifecycle matches MonoGame's `Game` class |
| Input action system | Three-layer architecture with correct `ActionPhase` state machine |
| Frame time infrastructure | `GameTime` + comprehensive `DebugStatsComponent` |

---

## Missing Engine Features (Roadmap, Not Bugs)

These are absent capabilities expected of a production-grade 2D/3D engine:

| Feature | Notes |
|---------|-------|
| **3D rendering** | No mesh loading, no 3D camera, no PBR pipeline |
| **Audio** | No sound system — OpenAL (via LWJGL) is the natural fit |
| **Fixed timestep / physics step** | Variable delta only; no fixed-step accumulator for deterministic simulation |
| **Asset pipeline** | No asset packing, no hot reload, no async loading |
| **Scene serialization** | Scenes and entities are code-only |
| **ECS query exclusions** | `Aspect.exclude(...)` not implemented |
| **Spatial partitioning** | No quadtree or grid for physics/culling queries |
