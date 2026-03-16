# Engine Audit

**Date:** 2026-03-16
**Scope:** Unresolved items from previous audits + fresh full codebase review

---

## Findings

| #  | Priority | Category    | Status | Summary                                                                                              |
|----|----------|-------------|--------|------------------------------------------------------------------------------------------------------|
| 1  | Critical | Correctness | Done   | Vulkan pipeline caching ignores render-pass changes                                                  |
| 2  | Critical | Leak        | Done   | Vulkan descriptor sets leak from a fixed-size shared pool                                            |
| 3  | High     | Bug         | Done   | `VulkanVertexBuffer.update(ByteBuffer)` stores byte count as float count                             |
| 4  | High     | Leak        | Done   | `ResourceLoader.load()` never closes Scanner / InputStream                                           |
| 5  | High     | Leak        | Done   | `ScreenPixelRatioHandler.set()` leaks native memory on exception                                     |
| 6  | High     | API         | Done   | Input APIs expose shared mutable state instead of stable snapshots                                   |
| 7  | High     | Design      | Done   | Screen lifecycle is unsafe: `show()` doubles as resume and transitions are immediate                 |
| 8  | High     | Design      | Done   | `Game.components` is a public mutable service list with hidden ordering and mutation hazards         |
| 9  | High     | Correctness | Done   | ECS entity handles are raw ids with no liveness or ownership validation                              |
| 10 | High     | Bug         | Done   | `Window.dispose()` calls GLFW after `glfwTerminate()`                                                |
| 11 | High     | Correctness | Done   | Vulkan sync objects are not recreated if swapchain image count changes                               |
| 12 | Medium   | Correctness | Done   | Vulkan `vSync=false` silently falls back to FIFO on Linux                                            |
| 13 | Medium   | GC          | Done   | `MouseState.position()` allocates `Vector2f` every call                                              |
| 14 | Medium   | API         | Done   | Vulkan backend silently ignores `setBlend()` and `setScissor()`                                      |
| 15 | Medium   | Build       | Done   | Deprecated Gradle multi-string dependency notation                                                   |
| 16 | Medium   | Feature     | Open   | Hierarchical transforms are still missing                                                            |
| 17 | Medium   | API         | Open   | `RenderTarget.getColorTexture()` ownership semantics need clarification                              |
| 18 | Medium   | Correctness | Open   | `Camera2D` dirty-flag correctness is easy to bypass through public mutable fields                    |
| 19 | Low      | Metrics     | Open   | `DebugStatsComponent` direct-memory fallback reports heap max                                        |
| 20 | Low      | GC          | Open   | `DebugStatsComponent.update()` uses `String.format()` per frame                                      |
| 21 | Low      | Portability | Open   | `module-info.java` missing `requires org.lwjgl.vulkan.natives`                                       |
| 22 | Low      | Design      | Open   | `RenderMode` enum stores backend-specific constants                                                  |
| 23 | Low      | GC          | Open   | `EntityManager.buildQuery()` allocates `HashSet` on cache miss                                       |

---

## Details

### 1. Critical — Vulkan pipeline caching ignores render-pass changes

**Files:** `VulkanShader.java`, `VulkanContext.java`

**Problem:** `VulkanShader.getOrCreatePipeline(...)` only invalidates the cached pipeline when the vertex layout changes. The active render pass is ignored, even though the same shader can render to the swapchain backbuffer or to a `VulkanRenderTarget`. Reusing a pipeline created for a different render pass is invalid and will eventually break off-screen rendering paths.

**Fix:** Cache pipelines by at least `(vertexLayout, renderPass)`. If more state becomes dynamic later, include every pipeline-defining input in the cache key.

---

### 2. Critical — Vulkan descriptor sets leak from a fixed-size shared pool

**Files:** `VulkanContext.java`, `VulkanTexture2D.java`, `VulkanRenderTarget.java`

**Problem:** The descriptor pool is allocated once with `maxSets = 256` and no `VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT`. Textures and render targets never free their descriptor sets on `dispose()`, and render-target resizes allocate new sets without releasing the old ones. Scenes exceeding 256 unique textures trigger `VK_ERROR_OUT_OF_POOL_MEMORY` and an unchecked crash, and heavy scene transitions guarantee exhaustion long before that hard limit.

**Fix:** Create the pool with `VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT` and free per-resource sets on `dispose()` and resize. For long-running scenes, implement a pool-of-pools: when allocation fails, create a new pool and add it to a list; recycle drained pools on scene transitions.

---

### 3. High — `VulkanVertexBuffer.update(ByteBuffer)` stores byte count as float count

**File:** `VulkanVertexBuffer.java` — `update(ByteBuffer data, int byteCount)`

**Problem:** The `update(ByteBuffer)` overload sets `this.length = byteCount`, but `getLength()` is expected to return float count. The `update(float[])` overload correctly sets `this.length = floatCount`. Any code calling `getLength()` after a ByteBuffer update gets a value 4× too large, corrupting vertex count calculations and draw call ranges.

**Fix:** `this.length = byteCount / Float.BYTES;`

---

### 4. High — `ResourceLoader.load()` never closes Scanner / InputStream

**File:** `ResourceLoader.java:80-88`

**Problem:** `load()` opens a `Scanner` on a classpath `InputStream`, reads it, then returns without closing either. Each call leaks one InputStream and one Scanner. Called during shader loading, font loading, and any text resource load.

**Fix:**
```java
public static String load(String path) {
    var in = ResourceLoader.class.getResourceAsStream(path);
    if (in == null) { throw ...; }
    try (var scanner = new Scanner(in, StandardCharsets.UTF_8)) {
        return scanner.useDelimiter("\\A").next();
    }
}
```

---

### 5. High — `ScreenPixelRatioHandler.set()` leaks native memory on exception

**File:** `ScreenPixelRatioHandler.java:29-48`

**Problem:** Four `memAllocInt(1)` buffers are allocated, then `memFree()` is called at the end of the method. If any line between allocation and free throws (e.g., divide-by-zero if `widthScreenCoordBuf.get()` returns 0), the buffers leak. This method is called during window creation and resize.

**Fix:** Wrap in `try-finally`, or better: use `MemoryStack.stackPush()` since these are small, short-lived allocations.

---

### 6. High — Input APIs expose shared mutable state instead of stable snapshots

**Files:** `InputSystem.java`, `KeyboardState.java`, `MouseState.java`, `InputActionContext.java`

**Problem:** The public input API behaves like a snapshot API, but parts of it are live shared state. `KeyboardState` and `MouseState` store the live arrays from `InputSystem` instead of copying them. `InputActionContext.readVector2()` returns the single static `REUSABLE_VECTOR2`. Values can therefore change underneath a caller during the same frame, and two callers can accidentally alias the same vector object.

**Fix:** Copy button arrays for keyboard and mouse snapshots, and return a fresh `Vector2f` from `readVector2()` or switch to an explicit output-parameter API.

---

### 7. High — Screen lifecycle is unsafe: `show()` doubles as resume and transitions are immediate

**Files:** `Screen.java`, `ScreenManager.java`, `PlayerScene.java`

**Problem:** `ScreenManager.pop()` resumes the previous screen by calling `show()` again. The current sample screen (`PlayerScene`) does heavy one-time work in `show()`: creating a batch, systems, entities, input actions, and textures. A hide/resume cycle therefore duplicates registrations and allocations. On top of that, `set()`, `push()`, and `pop()` execute immediately, so calling them inside `update()` or `draw()` can hide or dispose the currently executing screen mid-callback.

**Fix:** Split the lifecycle into distinct phases (`initialize/create`, `pause`, `resume`, `dispose`) and queue screen transitions so they are applied after the current frame finishes.

---

### 8. High — `Game.components` is a public mutable service list with hidden ordering and mutation hazards

**Files:** `Game.java`, `GameService.java`, `SandboxApplication.java`

**Problem:** The engine exposes `public final List<GameService> components`, then iterates it directly for resize, update, draw, and dispose. Behavior depends on insertion order, and runtime mutation is unsafe. The sandbox already relies on list order so UI draws after the scene.

**Fix:** Replace direct list access with `addService/removeService` APIs, support explicit priority or ordering, and apply mutations between frames instead of during iteration.

---

### 9. High — ECS entity handles are raw ids with no liveness or ownership validation

**Files:** `Entity.java`, `EntityManager.java`

**Problem:** `Entity` is just `record Entity(int id)`. `destroyEntity()` pushes ids back into the free list even if the entity was already destroyed or never belonged to that world, and `addComponent()` assumes the entity is still present. Cross-world or stale-handle misuse can corrupt id reuse or trigger null access.

**Fix:** Validate liveness and world ownership on every mutating ECS operation. Longer term, switch to generation/versioned entity handles instead of plain integer ids.

---

### 10. High — `Window.dispose()` calls GLFW after `glfwTerminate()`

**File:** `Window.java`

**Problem:** The teardown order clears the GLFW error callback after `glfwTerminate()`. Calling GLFW functions after termination is undefined and can become a shutdown crash on stricter platforms or toolchains.

**Fix:** Clear and free the error callback before calling `glfwTerminate()`.

---

### 11. High — Vulkan sync objects are not recreated if swapchain image count changes

**Files:** `VulkanContext.java`, `VulkanSyncObjects.java`

**Problem:** `VulkanSyncObjects` is sized once from the original swapchain image count. `recreateSwapChain()` rebuilds the swapchain and framebuffers, but not the sync objects. If the new swapchain uses a different image count, `currentImageIndex` can address mismatched semaphore storage.

**Fix:** Recreate sync objects whenever the swapchain is recreated, or redesign synchronization ownership so it is independent of swapchain image count.

---

### 12. Medium — Vulkan `vSync=false` silently falls back to FIFO on Linux

**File:** `VulkanSwapChain.java`

**Problem:** `VK_PRESENT_MODE_MAILBOX_KHR` is frequently unavailable on Linux/X11 + Mesa drivers. The code silently falls back to `FIFO`, making `setVSync(false)` ineffective on those platforms with no log output.

**Fix:** Try `VK_PRESENT_MODE_IMMEDIATE_KHR` before falling back to `FIFO`. Log a warning when the requested mode is unavailable: `log.warn("MAILBOX not available, falling back to {}", actualMode)`.

---

### 13. Medium — `MouseState.position()` allocates `Vector2f` every call

**File:** `MouseState.java:44-46`

**Problem:** Returns `new Vector2f(x, y)` on every invocation. Game code calling `Mouse.getState().position()` in the update loop creates garbage every frame.

**Fix:** Add a `position(Vector2f dest)` overload that writes into a caller-supplied vector. Keep the allocating version for convenience but document that the dest-accepting version is preferred in hot paths.

---

### 14. Medium — Vulkan backend silently ignores `setBlend()` and `setScissor()`

**Files:** `GraphicsContext.java` (default methods), `VulkanContext.java` (no override)

**Problem:** `GraphicsContext` declares `setBlend()` and `setScissor()` as default no-ops. `VulkanContext` never overrides them. `SpriteBatch2D.flush()` calls both, which silently do nothing.

**Fix:** Override in `VulkanContext` to at least `log.warn()` on first call, making the gap visible. Full implementation requires dynamic pipeline state (Vulkan 1.3 `VK_DYNAMIC_STATE_COLOR_BLEND_ENABLE`), which can be deferred.

---

### 15. Medium — Deprecated Gradle multi-string dependency notation

**File:** `core/build.gradle.kts`

**Problem:** All dependencies use the `implementation("org.lwjgl", "lwjgl")` multi-argument form. Gradle has deprecated this in favour of the single-string `"org.lwjgl:lwjgl"` notation and will remove support in Gradle 10.

**Fix:** Replace all `("group", "artifact")` with `"group:artifact"` throughout both build files.

---

### 16. Medium — Hierarchical transforms are still missing

**File:** `Transform2DComponent.java`

**Problem:** `Transform2DComponent` only stores absolute local state (`position`, `rotation`, `scale`, `z`) and has no parent relationship. Grouped motion, attached weapons, camera rigs, layered UI roots, and prefab composition all require manual propagation in game code.

**Fix:** Add parent/child transform support and a transform-propagation system that computes stable world transforms from local transforms.

---

### 17. Medium — `RenderTarget.getColorTexture()` ownership semantics need clarification

**Files:** `RenderTarget.java`, `VulkanRenderTarget.java`

**Problem:** The returned texture from `getColorTexture()` is a non-owning view whose `dispose()` is intentionally a no-op. The ownership semantics should be documented explicitly in the API so callers understand the lifetime rules.

**Fix:** Encode ownership explicitly in the API so callers cannot accidentally misuse the returned texture reference.

---

### 18. Medium — `Camera2D` dirty-flag correctness is easy to bypass through public mutable fields

**Files:** `Camera2D.java`, `OrthographicCamera2D.java`

**Problem:** `position`, `zoom`, and `rotation` are publicly mutable, but the dirty flag is only updated by the setter methods or `markDirty()`. Direct field mutation can leave the cached camera matrix stale while still looking like valid usage.

**Fix:** Make these fields private and force changes through setters, or replace the mutable public vector with an observable wrapper that marks the camera dirty automatically.

---

### 19. Low — `DebugStatsComponent` direct-memory fallback reports heap max

**File:** `DebugStatsComponent.java:138-147`

**Problem:** When the reflective `jdk.internal.misc.VM.maxDirectMemory()` call fails, the fallback is `Runtime.getRuntime().maxMemory()`, which is the heap max — a completely different metric. The off-heap usage percentage displayed to the developer will be wrong.

**Fix:** Use `-1` as sentinel for "unknown" and display "N/A" instead of an incorrect percentage.

---

### 20. Low — `DebugStatsComponent.update()` uses `String.format()` per frame

**File:** `DebugStatsComponent.java:283-293`

**Problem:** ~8 `String.format()` calls run every frame in `update()`, each allocating a new String. While this is debug-only code behind an F3 toggle, it adds measurable GC noise when enabled.

**Fix:** Use a shared `StringBuilder` that is cleared and reused each frame, or only recompute strings when values actually change (dirty flag on the stats).

---

### 21. Low — `module-info.java` missing `requires org.lwjgl.vulkan.natives`

**File:** `core/src/main/java/module-info.java`

**Problem:** The module requires `org.lwjgl.vulkan` but not `org.lwjgl.vulkan.natives`. On macOS this works because MoltenVK is bundled differently, but Linux and Windows builds will fail to resolve the Vulkan native libraries at runtime.

**Fix:** Add `requires org.lwjgl.vulkan.natives;` after the existing Vulkan require. Verify consistency with how other LWJGL modules (`glfw`, `stb`, `vma`) are declared — all of which correctly have their `.natives` counterpart.

---

### 22. Low — `RenderMode` enum stores backend-specific constants

**File:** `RenderMode.java`

**Problem:** `RenderMode.TRIANGLES`, `.LINES`, `.POINTS` store backend-specific integer constants. The enum is part of the public API, and the Vulkan backend sets pipeline topology at pipeline creation, not via these values.

**Fix:** Remove the constant field entirely and let the backend map the enum to its own constant internally.

---

### 23. Low — `EntityManager.buildQuery()` allocates `HashSet` on cache miss

**File:** `EntityManager.java:88-112`

**Problem:** Every cache miss creates `new HashSet<>(map.keySet())` plus `new ArrayList<Entity>()` with `new Entity(id)` per result. The versioned cache mitigates frequency, but particle-heavy scenes with frequent spawn/despawn can still trigger rebuilds every frame.

**Fix:** Low urgency given the version cache. If it becomes a bottleneck: maintain a per-archetype entity list updated incrementally on `addComponent` / `removeComponent` instead of rebuilding from scratch.

---

