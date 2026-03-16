# Engine Audit

**Date:** 2026-03-16
**Scope:** Unresolved items from previous audits + fresh full codebase review

---

## Findings

| #  | Priority | Category    | Status | Summary                                                                                              |
|----|----------|-------------|--------|------------------------------------------------------------------------------------------------------|
| 1  | Critical | Bug         | Open   | OpenGL texture upload is incorrect for RGB assets                                                    |
| 2  | Critical | Leak        | Open   | `OpenGLShader.dispose()` leaks separable program objects                                             |
| 3  | Critical | Leak        | Open   | `OpenGLVertexArray.dispose()` does not release attached buffers, so `SpriteBatch2D` leaks GPU memory |
| 4  | Critical | Correctness | Done   | Vulkan pipeline caching ignores render-pass changes                                                  |
| 5  | Critical | Leak        | Done   | Vulkan descriptor sets leak from a fixed-size shared pool                                            |
| 6  | High     | Bug         | Open   | `VulkanVertexBuffer.update(ByteBuffer)` stores byte count as float count                             |
| 7  | High     | Leak        | Open   | `ResourceLoader.load()` never closes Scanner / InputStream                                           |
| 8  | High     | Leak        | Open   | `ScreenPixelRatioHandler.set()` leaks native memory on exception                                     |
| 9  | High     | API         | Open   | Input APIs expose shared mutable state instead of stable snapshots                                   |
| 10 | High     | Design      | Open   | Screen lifecycle is unsafe: `show()` doubles as resume and transitions are immediate                 |
| 11 | High     | Design      | Open   | `Game.components` is a public mutable service list with hidden ordering and mutation hazards         |
| 12 | High     | Correctness | Open   | ECS entity handles are raw ids with no liveness or ownership validation                              |
| 13 | High     | Bug         | Open   | `Window.dispose()` calls GLFW after `glfwTerminate()`                                                |
| 14 | High     | Correctness | Open   | Vulkan sync objects are not recreated if swapchain image count changes                               |
| 15 | Medium   | Correctness | Open   | Vulkan `vSync=false` silently falls back to FIFO on Linux                                            |
| 16 | Medium   | GC          | Open   | `MouseState.position()` allocates `Vector2f` every call                                              |
| 17 | Medium   | API         | Open   | Vulkan backend silently ignores `setBlend()` and `setScissor()`                                      |
| 18 | Medium   | API         | Open   | `OpenGLTexture2D.bind()` hardcodes texture unit 0                                                    |
| 19 | Medium   | Build       | Open   | Deprecated Gradle multi-string dependency notation                                                   |
| 20 | Medium   | Feature     | Open   | Hierarchical transforms are still missing                                                            |
| 21 | Medium   | API         | Open   | `RenderTarget.getColorTexture()` has backend-inconsistent ownership semantics                        |
| 22 | Medium   | Correctness | Open   | `Camera2D` dirty-flag correctness is easy to bypass through public mutable fields                    |
| 23 | Low      | Metrics     | Open   | `DebugStatsComponent` direct-memory fallback reports heap max                                        |
| 24 | Low      | GC          | Open   | `DebugStatsComponent.update()` uses `String.format()` per frame                                      |
| 25 | Low      | Portability | Open   | `module-info.java` missing `requires org.lwjgl.vulkan.natives`                                       |
| 26 | Low      | Design      | Open   | `RenderMode` enum stores GL constants but is used by Vulkan                                          |
| 27 | Low      | GC          | Open   | `EntityManager.buildQuery()` allocates `HashSet` on cache miss                                       |
| 28 | Low      | Leak        | Open   | OpenGL debug callback is created but never freed                                                     |

---

## Details

### 1. Critical — OpenGL texture upload is incorrect for RGB assets

**Files:** `OpenGLTexture2D.java`, `TextureLoader.java`

**Problem:** `TextureLoader` preserves the source channel count, but `OpenGLTexture2D` always uploads pixel data with `GL_RGBA`. For 3-channel textures, the engine tells OpenGL to read 4 bytes per pixel from a 3-byte buffer. That can produce corrupted colors and out-of-bounds reads.

**Fix:** Pick one policy and apply it consistently: either force STB to output RGBA everywhere, or use the same channel-derived format for both the internal format and the upload format.

---

### 2. Critical — `OpenGLShader.dispose()` leaks separable program objects

**File:** `OpenGLShader.java`

**Problem:** `OpenGLShader` creates separate vertex and fragment program objects with `glCreateShaderProgramv(...)`, but `dispose()` only deletes the program pipeline. The actual program objects remain live on the GPU.

**Fix:** Delete `vertexId` and `fragmentId` explicitly in `dispose()`, in addition to deleting the pipeline object.

---

### 3. Critical — `OpenGLVertexArray.dispose()` does not release attached buffers

**Files:** `OpenGLVertexArray.java`, `SpriteBatch2D.java`

**Problem:** `SpriteBatch2D.dispose()` assumes the vertex array owns the dynamic VBO and EBO. That is true in the Vulkan backend, but the OpenGL backend only deletes the VAO object itself. The result is a backend-specific GPU leak every time a sprite batch is created and disposed.

**Fix:** Make ownership explicit and consistent across backends. The simplest fix is to make `VertexArray.dispose()` release attached VBOs and the EBO in the OpenGL implementation as well.

---

### 4. Critical — Vulkan pipeline caching ignores render-pass changes

**Files:** `VulkanShader.java`, `VulkanContext.java`

**Problem:** `VulkanShader.getOrCreatePipeline(...)` only invalidates the cached pipeline when the vertex layout changes. The active render pass is ignored, even though the same shader can render to the swapchain backbuffer or to a `VulkanRenderTarget`. Reusing a pipeline created for a different render pass is invalid and will eventually break off-screen rendering paths.

**Fix:** Cache pipelines by at least `(vertexLayout, renderPass)`. If more state becomes dynamic later, include every pipeline-defining input in the cache key.

---

### 5. Critical — Vulkan descriptor sets leak from a fixed-size shared pool

**Files:** `VulkanContext.java`, `VulkanTexture2D.java`, `VulkanRenderTarget.java`

**Problem:** The descriptor pool is allocated once with `maxSets = 256` and no `VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT`. Textures and render targets never free their descriptor sets on `dispose()`, and render-target resizes allocate new sets without releasing the old ones. Scenes exceeding 256 unique textures trigger `VK_ERROR_OUT_OF_POOL_MEMORY` and an unchecked crash, and heavy scene transitions guarantee exhaustion long before that hard limit.

**Fix:** Create the pool with `VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT` and free per-resource sets on `dispose()` and resize. For long-running scenes, implement a pool-of-pools: when allocation fails, create a new pool and add it to a list; recycle drained pools on scene transitions.

---

### 6. High — `VulkanVertexBuffer.update(ByteBuffer)` stores byte count as float count

**File:** `VulkanVertexBuffer.java` — `update(ByteBuffer data, int byteCount)`

**Problem:** The `update(ByteBuffer)` overload sets `this.length = byteCount`, but `getLength()` is expected to return float count. The `update(float[])` overload correctly sets `this.length = floatCount`. Any code calling `getLength()` after a ByteBuffer update gets a value 4× too large, corrupting vertex count calculations and draw call ranges.

**Fix:** `this.length = byteCount / Float.BYTES;`

---

### 7. High — `ResourceLoader.load()` never closes Scanner / InputStream

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

### 8. High — `ScreenPixelRatioHandler.set()` leaks native memory on exception

**File:** `ScreenPixelRatioHandler.java:29-48`

**Problem:** Four `memAllocInt(1)` buffers are allocated, then `memFree()` is called at the end of the method. If any line between allocation and free throws (e.g., divide-by-zero if `widthScreenCoordBuf.get()` returns 0), the buffers leak. This method is called during window creation and resize.

**Fix:** Wrap in `try-finally`, or better: use `MemoryStack.stackPush()` since these are small, short-lived allocations.

---

### 9. High — Input APIs expose shared mutable state instead of stable snapshots

**Files:** `InputSystem.java`, `KeyboardState.java`, `MouseState.java`, `InputActionContext.java`

**Problem:** The public input API behaves like a snapshot API, but parts of it are live shared state. `KeyboardState` and `MouseState` store the live arrays from `InputSystem` instead of copying them. `InputActionContext.readVector2()` returns the single static `REUSABLE_VECTOR2`. Values can therefore change underneath a caller during the same frame, and two callers can accidentally alias the same vector object.

**Fix:** Copy button arrays for keyboard and mouse snapshots, and return a fresh `Vector2f` from `readVector2()` or switch to an explicit output-parameter API.

---

### 10. High — Screen lifecycle is unsafe: `show()` doubles as resume and transitions are immediate

**Files:** `Screen.java`, `ScreenManager.java`, `PlayerScene.java`

**Problem:** `ScreenManager.pop()` resumes the previous screen by calling `show()` again. The current sample screen (`PlayerScene`) does heavy one-time work in `show()`: creating a batch, systems, entities, input actions, and textures. A hide/resume cycle therefore duplicates registrations and allocations. On top of that, `set()`, `push()`, and `pop()` execute immediately, so calling them inside `update()` or `draw()` can hide or dispose the currently executing screen mid-callback.

**Fix:** Split the lifecycle into distinct phases (`initialize/create`, `pause`, `resume`, `dispose`) and queue screen transitions so they are applied after the current frame finishes.

---

### 11. High — `Game.components` is a public mutable service list with hidden ordering and mutation hazards

**Files:** `Game.java`, `GameService.java`, `SandboxApplication.java`

**Problem:** The engine exposes `public final List<GameService> components`, then iterates it directly for resize, update, draw, and dispose. Behavior depends on insertion order, and runtime mutation is unsafe. The sandbox already relies on list order so UI draws after the scene.

**Fix:** Replace direct list access with `addService/removeService` APIs, support explicit priority or ordering, and apply mutations between frames instead of during iteration.

---

### 12. High — ECS entity handles are raw ids with no liveness or ownership validation

**Files:** `Entity.java`, `EntityManager.java`

**Problem:** `Entity` is just `record Entity(int id)`. `destroyEntity()` pushes ids back into the free list even if the entity was already destroyed or never belonged to that world, and `addComponent()` assumes the entity is still present. Cross-world or stale-handle misuse can corrupt id reuse or trigger null access.

**Fix:** Validate liveness and world ownership on every mutating ECS operation. Longer term, switch to generation/versioned entity handles instead of plain integer ids.

---

### 13. High — `Window.dispose()` calls GLFW after `glfwTerminate()`

**File:** `Window.java`

**Problem:** The teardown order clears the GLFW error callback after `glfwTerminate()`. Calling GLFW functions after termination is undefined and can become a shutdown crash on stricter platforms or toolchains.

**Fix:** Clear and free the error callback before calling `glfwTerminate()`.

---

### 14. High — Vulkan sync objects are not recreated if swapchain image count changes

**Files:** `VulkanContext.java`, `VulkanSyncObjects.java`

**Problem:** `VulkanSyncObjects` is sized once from the original swapchain image count. `recreateSwapChain()` rebuilds the swapchain and framebuffers, but not the sync objects. If the new swapchain uses a different image count, `currentImageIndex` can address mismatched semaphore storage.

**Fix:** Recreate sync objects whenever the swapchain is recreated, or redesign synchronization ownership so it is independent of swapchain image count.

---

### 15. Medium — Vulkan `vSync=false` silently falls back to FIFO on Linux

**File:** `VulkanSwapChain.java`

**Problem:** `VK_PRESENT_MODE_MAILBOX_KHR` is frequently unavailable on Linux/X11 + Mesa drivers. The code silently falls back to `FIFO`, making `setVSync(false)` ineffective on those platforms with no log output.

**Fix:** Try `VK_PRESENT_MODE_IMMEDIATE_KHR` before falling back to `FIFO`. Log a warning when the requested mode is unavailable: `log.warn("MAILBOX not available, falling back to {}", actualMode)`.

---

### 16. Medium — `MouseState.position()` allocates `Vector2f` every call

**File:** `MouseState.java:44-46`

**Problem:** Returns `new Vector2f(x, y)` on every invocation. Game code calling `Mouse.getState().position()` in the update loop creates garbage every frame.

**Fix:** Add a `position(Vector2f dest)` overload that writes into a caller-supplied vector. Keep the allocating version for convenience but document that the dest-accepting version is preferred in hot paths.

---

### 17. Medium — Vulkan backend silently ignores `setBlend()` and `setScissor()`

**Files:** `GraphicsContext.java` (default methods), `VulkanContext.java` (no override)

**Problem:** `GraphicsContext` declares `setBlend()` and `setScissor()` as default no-ops. `VulkanContext` never overrides them. `SpriteBatch2D.flush()` calls both, which silently do nothing on Vulkan. Users switching backends get different blending behaviour with no indication.

**Fix:** Override in `VulkanContext` to at least `log.warn()` on first call, making the gap visible. Full implementation requires dynamic pipeline state (Vulkan 1.3 `VK_DYNAMIC_STATE_COLOR_BLEND_ENABLE`), which can be deferred.

---

### 18. Medium — `OpenGLTexture2D.bind()` hardcodes texture unit 0

**File:** `OpenGLTexture2D.java:89`

**Problem:** `bind()` calls `glActiveTexture(GL_TEXTURE0)` unconditionally. The `Texture2D` interface has no `bind(int unit)` overload. Multi-texture shaders (normal maps, lightmaps, shadow maps) are structurally impossible.

**Fix:** Add `bind(int unit)` to `Texture2D`. Implement as `glActiveTexture(GL_TEXTURE0 + unit)` in OpenGL. Keep `bind()` as shorthand for `bind(0)`.

---

### 19. Medium — Deprecated Gradle multi-string dependency notation

**File:** `core/build.gradle.kts`

**Problem:** All dependencies use the `implementation("org.lwjgl", "lwjgl")` multi-argument form. Gradle has deprecated this in favour of the single-string `"org.lwjgl:lwjgl"` notation and will remove support in Gradle 10.

**Fix:** Replace all `("group", "artifact")` with `"group:artifact"` throughout both build files.

---

### 20. Medium — Hierarchical transforms are still missing

**File:** `Transform2DComponent.java`

**Problem:** `Transform2DComponent` only stores absolute local state (`position`, `rotation`, `scale`, `z`) and has no parent relationship. Grouped motion, attached weapons, camera rigs, layered UI roots, and prefab composition all require manual propagation in game code.

**Fix:** Add parent/child transform support and a transform-propagation system that computes stable world transforms from local transforms.

---

### 21. Medium — `RenderTarget.getColorTexture()` has backend-inconsistent ownership semantics

**Files:** `RenderTarget.java`, `OpenGLRenderTarget.java`, `VulkanRenderTarget.java`

**Problem:** In OpenGL, `getColorTexture()` returns an owning texture object whose `dispose()` deletes the real attachment. In Vulkan, the returned texture is a non-owning view whose `dispose()` is intentionally a no-op. The same API therefore has opposite lifetime rules depending on the active backend.

**Fix:** Make both backends return non-owning attachment views, or encode ownership explicitly in the API so callers cannot accidentally delete a live render-target attachment on one backend only.

---

### 22. Medium — `Camera2D` dirty-flag correctness is easy to bypass through public mutable fields

**Files:** `Camera2D.java`, `OrthographicCamera2D.java`

**Problem:** `position`, `zoom`, and `rotation` are publicly mutable, but the dirty flag is only updated by the setter methods or `markDirty()`. Direct field mutation can leave the cached camera matrix stale while still looking like valid usage.

**Fix:** Make these fields private and force changes through setters, or replace the mutable public vector with an observable wrapper that marks the camera dirty automatically.

---

### 23. Low — `DebugStatsComponent` direct-memory fallback reports heap max

**File:** `DebugStatsComponent.java:138-147`

**Problem:** When the reflective `jdk.internal.misc.VM.maxDirectMemory()` call fails, the fallback is `Runtime.getRuntime().maxMemory()`, which is the heap max — a completely different metric. The off-heap usage percentage displayed to the developer will be wrong.

**Fix:** Use `-1` as sentinel for "unknown" and display "N/A" instead of an incorrect percentage.

---

### 24. Low — `DebugStatsComponent.update()` uses `String.format()` per frame

**File:** `DebugStatsComponent.java:283-293`

**Problem:** ~8 `String.format()` calls run every frame in `update()`, each allocating a new String. While this is debug-only code behind an F3 toggle, it adds measurable GC noise when enabled.

**Fix:** Use a shared `StringBuilder` that is cleared and reused each frame, or only recompute strings when values actually change (dirty flag on the stats).

---

### 25. Low — `module-info.java` missing `requires org.lwjgl.vulkan.natives`

**File:** `core/src/main/java/module-info.java`

**Problem:** The module requires `org.lwjgl.vulkan` but not `org.lwjgl.vulkan.natives`. On macOS this works because MoltenVK is bundled differently, but Linux and Windows builds will fail to resolve the Vulkan native libraries at runtime.

**Fix:** Add `requires org.lwjgl.vulkan.natives;` after the existing Vulkan require. Verify consistency with how other LWJGL modules (`opengl`, `glfw`, `stb`, `vma`) are declared — all of which correctly have their `.natives` counterpart.

---

### 26. Low — `RenderMode` enum stores GL constants but is used by Vulkan

**File:** `RenderMode.java`

**Problem:** `RenderMode.TRIANGLES`, `.LINES`, `.POINTS` store OpenGL `GL_*` integer constants in a `glRef` field. Vulkan never reads these values (its pipeline topology is set at pipeline creation), but the enum is part of the public API and misleadingly named. New contributors will expect these values to work on Vulkan.

**Fix:** Rename the field from `glRef` to something backend-neutral, or remove it entirely and let each backend map the enum to its own constant internally.

---

### 27. Low — `EntityManager.buildQuery()` allocates `HashSet` on cache miss

**File:** `EntityManager.java:88-112`

**Problem:** Every cache miss creates `new HashSet<>(map.keySet())` plus `new ArrayList<Entity>()` with `new Entity(id)` per result. The versioned cache mitigates frequency, but particle-heavy scenes with frequent spawn/despawn can still trigger rebuilds every frame.

**Fix:** Low urgency given the version cache. If it becomes a bottleneck: maintain a per-archetype entity list updated incrementally on `addComponent` / `removeComponent` instead of rebuilding from scratch.

---

### 28. Low — OpenGL debug callback is created but never freed

**File:** `OpenGLGraphicsContext.java`

**Problem:** `GLUtil.setupDebugMessageCallback()` returns a native callback handle, but the return value is discarded and `OpenGLGraphicsContext.dispose()` is empty. That leaves native debug-callback state unmanaged for the entire process lifetime.

**Fix:** Store the callback handle in the graphics context and free it during OpenGL context disposal.
