# Vulkan Engine Audit

Date: 2026-03-18

Scope: review of the current Vulkan backend and the engine abstractions around it in `core/`.

Build status: `./gradlew build` succeeds on the current tree. The issues below are runtime-correctness, scalability, and architecture problems, not compile failures.

## Overall assessment

The backend is beyond a toy spike: it creates devices, uploads buffers and textures, renders sprites, and supports off-screen targets. The problem is that it is still shaped like a tutorial renderer, while the surrounding engine API already claims to be a reusable backend-agnostic engine. The biggest risks are:

1. frame lifecycle bugs around swapchain loss / resize,
2. memory and ownership semantics that are too implicit for Vulkan,
3. a fake HAL that hardcodes Vulkan through the public API,
4. expensive `waitIdle` / `queueWaitIdle` usage that will collapse as content grows.

## Findings

### 1. DONE: frame acquisition failure can leave the engine submitting an invalid frame

`VulkanContext.clear()` returns early when `vkAcquireNextImageKHR` yields `VK_ERROR_OUT_OF_DATE_KHR`, but it does not mark the frame as invalid or stop the rest of the engine from continuing. `Game.loop()` still runs normal draw code and always calls `Renderer.swapBuffers(...)`, which then ends and submits a command buffer that may never have been begun.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:246-285`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:379-418`
- `core/src/main/java/hu/mudlee/core/Game.java:101-124`
- `core/src/main/java/hu/mudlee/core/GraphicsDevice.java:18-22`

Why this is bad:

- resize/minimize races are normal in Vulkan,
- the current contract relies on call ordering rather than an explicit frame state,
- this will produce runtime failures exactly in the window-management paths that users hit first.

What I would change:

- Replace `clear()` with `beginFrame()` returning `boolean` or a frame object.
- Skip all render work and presentation when image acquisition fails.
- Make `swapBuffers()` legal only after a successful frame begin.

### 2. DONE: High: swapchain recreation is incomplete and assumes the render pass remains compatible

The swapchain is recreated in `VulkanContext.recreateSwapChain()`, but the main render pass is not. The initial render pass is created from `swapChain.imageFormat()` during startup only. If the surface format changes during recreation, the render pass and pipelines become incompatible with the new swapchain images.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:220-224`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:522-535`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanRenderPass.java:24-37`

Why this is bad:

- render pass compatibility is a real Vulkan contract, not a soft preference,
- format changes do happen across platforms and surface transitions,
- the current code will fail in a way that is hard to diagnose later.

What I would change:

- Detect swapchain format changes during recreation.
- Rebuild the render pass when the format changes.
- Invalidate all dependent graphics pipelines and rebuild framebuffers afterward.

### 3. DONE: High: initialization and shutdown lifetime handling is unsafe

`VulkanContext.windowCreated()` constructs native objects in a straight line with no rollback if a later step fails. The constructor also publishes `VulkanContext.instance` immediately, but `dispose()` never clears it. Several resources call back into `VulkanContext.get()` during disposal.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:85-88`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:201-229`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:433-458`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanTexture2D.java:100-115`

Why this is bad:

- partial init leaks native Vulkan handles,
- resource disposal after renderer shutdown can become a use-after-free pattern,
- global singleton state is doing ownership work it should not do.

What I would change:

- Build Vulkan startup as staged ownership with rollback on failure.
- Clear the singleton on shutdown, or preferably remove the global singleton entirely.
- Pass explicit backend/device ownership into resources instead of letting them reach back into global state.

### 4. DONE: High: the backend abstraction is not real

The code exposes `RenderBackend`, `GraphicsDeviceManager.setPreferredBackend(...)`, and `GraphicsDevice.getBackend()`, but the engine-level factories instantiate Vulkan directly everywhere. `Renderer` always constructs `new VulkanContext(true)`, and the main render resource abstractions directly create Vulkan implementations.

References:

- `core/src/main/java/hu/mudlee/core/render/Renderer.java:17-19`
- `core/src/main/java/hu/mudlee/core/render/VertexArray.java:7-10`
- `core/src/main/java/hu/mudlee/core/render/VertexBuffer.java:5-12`
- `core/src/main/java/hu/mudlee/core/render/Shader.java:6-9`
- `core/src/main/java/hu/mudlee/core/render/texture/Texture2D.java:7-18`
- `core/src/main/java/hu/mudlee/core/render/RenderTarget.java:23-27`

Why this is bad:

- the public API advertises a HAL that does not exist,
- adding OpenGL back, validating a second backend, or unit-testing backend selection will require rewriting the public render layer,
- this creates false confidence in the engine architecture.

What I would change:

- Introduce a backend factory owned by `Renderer` or `GraphicsDevice`.
- Move resource creation behind backend-owned factories or a proper `GraphicsDevice`.
- Remove backend selection from the public API until the abstraction is real.

### 5. DONE: High: memory allocation semantics are too loose for Vulkan

`VulkanBuffer` accepts requested memory flags, but only actually branches on `HOST_VISIBLE`. It does not preserve coherence requirements and never flushes mapped allocations. Callers currently assume `HOST_VISIBLE | HOST_COHERENT` staging and dynamic buffer behavior.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanBuffer.java:29-58`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanBuffer.java:69-75`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanVertexBuffer.java:44-57`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanVertexBuffer.java:76-83`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanTexture2D.java:145-170`

Why this is bad:

- Vulkan memory visibility rules are explicit,
- the current code is relying on VMA defaults rather than a defined contract,
- this can produce stale or platform-specific upload bugs later.

What I would change:

- Model required and preferred allocation flags explicitly.
- Track whether an allocation is coherent.
- Flush and invalidate mapped memory when needed instead of assuming coherence.

### 6. High: draw submission still depends on hidden global texture state

`Texture2D.bind()` mutates `VulkanContext.activeDescriptorSet`, and `VulkanContext.renderRaw()` consumes that implicit global. `SpriteBatch2D.flush()` relies on calling `currentTexture.bind()` just before `Renderer.renderRaw(...)`.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:82`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:133-135`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:343-347`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanTexture2D.java:90-94`
- `core/src/main/java/hu/mudlee/core/render/SpriteBatch2D.java:249-259`

Why this is bad:

- the correctness of a draw depends on side effects outside the draw API,
- it blocks safe multithreaded command recording,
- it is exactly the style of implicit state Vulkan is supposed to eliminate.

What I would change:

- Remove public `Texture2D.bind()` from the engine API.
- Submit texture/material bindings explicitly as part of a draw command or pass encoder.
- Keep descriptor state inside backend-owned frame/pass objects.

### 7. DONE: High: the vertex input model is inconsistent with the API it exposes

`VulkanVertexArray` accepts multiple VBOs and exposes instancing, but `VulkanShader.createGraphicsPipeline(...)` hardcodes one vertex binding at binding `0`, all attributes also use binding `0`, and input rate is always per-vertex. The pipeline cache also compares layouts by reference identity instead of value and only remembers one pipeline.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanVertexArray.java:23-30`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanVertexArray.java:47-80`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:349-357`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanShader.java:89-99`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanShader.java:223-244`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanShader.java:322-340`

Why this is bad:

- the API claims capabilities the backend does not actually implement,
- multiple vertex bindings or instance-rate buffers will fetch incorrect data,
- pipeline churn will be worse than necessary once more passes are added.

What I would change:

- Redesign vertex input around explicit binding descriptions per bound buffer.
- Encode per-instance input rate in the layout model.
- Use a pipeline cache keyed by a stable pipeline signature, not one mutable slot.
- Respect normalized vertex attributes when choosing Vulkan formats.

### 8. DONE: High: ownership is modeled like OpenGL objects, not Vulkan resources

`VulkanVertexArray.dispose()` recursively disposes every attached vertex buffer and the index buffer. That makes buffer sharing unsafe and creates double-destroy risk. The public abstraction itself still treats `VertexArray` like the owner of the buffers it references.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanVertexArray.java:72-80`
- `core/src/main/java/hu/mudlee/core/render/VertexArray.java:12-26`

Why this is bad:

- Vulkan buffers are independent resources and are often shared,
- ownership should be explicit and singular,
- recursive disposal through a binding object is a fragile lifetime model.

What I would change:

- Make buffers first-class owned resources.
- Turn the current vertex-array concept into a lightweight binding description or mesh view.
- Make disposal idempotent and tied to the true owner.

### 9. DONE: Medium: the engine spends too much time idling the whole device or graphics queue

Single-use transfer helpers end with `vkQueueWaitIdle`, swapchain recreation uses `device.waitIdle()`, and render-target resize/dispose also call `waitIdle()`.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanCommandPool.java:72-104`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:522-535`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanRenderTarget.java:88-107`

Why this is bad:

- it is simple but highly serializing,
- content load time and dynamic render-target behavior will scale badly,
- it prevents evolving toward async uploads or multithreaded recording.

What I would change:

- Replace queue-wide idle waits with fence-based upload completion.
- Defer resource destruction until the relevant in-flight frames have completed.
- Prefer a dedicated transfer path when the selected device exposes one.

### 10. DONE: Medium: swapchain creation follows minimums rather than engine-grade defaults

The swapchain requests `capabilities.minImageCount()` exactly and throws away the old swapchain during recreation instead of passing it through `oldSwapchain`.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanSwapChain.java:58-68`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanSwapChain.java:78-83`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanSwapChain.java:157-163`

Why this is bad:

- minimal buffering leaves less room for smooth FIFO presentation,
- not using `oldSwapchain` is a poor Vulkan recreation pattern,
- resize churn will be worse than it needs to be.

What I would change:

- Request `minImageCount + 1`, clamped to `maxImageCount` when needed.
- Pass the previous swapchain handle in `oldSwapchain` and destroy it only after the new chain is ready.

### 11. DONE: Medium: the render API encodes pass ownership as call ordering instead of explicit objects

`Game.loop()` lets arbitrary draw code run between clear and present, `GraphicsDevice` only exposes `clear`, `VulkanContext.setRenderTarget(...)` can end and restart passes at arbitrary times, and `RenderContext` is only a marker interface. Systems like `SpriteRender2DSystem` degrade into `instanceof` checks and silent no-ops.

References:

- `core/src/main/java/hu/mudlee/core/Game.java:101-124`
- `core/src/main/java/hu/mudlee/core/GraphicsDevice.java:18-29`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanContext.java:289-297`
- `core/src/main/java/hu/mudlee/core/render/RenderContext.java:3-20`
- `core/src/main/java/hu/mudlee/core/ecs/system/SpriteRender2DSystem.java:31-35`

Why this is bad:

- pass lifetime is implicit and easy to misuse,
- adding multipass rendering, depth, post-processing, or async uploads will get messy quickly,
- systems silently stop rendering when given the wrong context type.

What I would change:

- Add explicit `beginFrame`, `beginRenderPass`, `endRenderPass`, `present` concepts.
- Replace marker `RenderContext` usage with typed pass interfaces.
- Make incorrect usage fail structurally, not through runtime guesswork.

### 12. DONE: Medium: platform and portability support is too narrow

The instance/device setup targets a simple desktop Vulkan path. Device creation only requires `VK_KHR_swapchain`, and selection/creation does not account for portability subsets or a declared feature manifest.

References:

- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanInstance.java:37-60`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanInstance.java:102-118`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanDevice.java:19`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanDevice.java:113-166`
- `core/src/main/java/hu/mudlee/core/render/vulkan/VulkanDevice.java:199-227`

Why this is bad:

- the repo is clearly expected to run on macOS as well,
- portability implementations need explicit handling,
- the renderer is not validating the feature contract it actually depends on.

What I would change:

- Build instance/device creation from an explicit required-capabilities manifest.
- Handle portability extensions conditionally when exposed.
- Validate the selected device against future renderer requirements up front.

## What is implemented in a bad way

These are the patterns I would consider fundamentally wrong for an engine, not just incomplete:

1. The fake backend abstraction. The engine should either be Vulkan-only for now, or truly backend-driven. The current middle state is misleading.
2. Global mutable render state via `Texture2D.bind()` and `activeDescriptorSet`. That is an OpenGL-era pattern hidden inside a Vulkan backend.
3. Resource ownership through container objects like `VulkanVertexArray`. Vulkan needs explicit ownership and lifetime.
4. Frame/pass lifecycle expressed as "call these methods in the right order". A real engine needs explicit frame and pass objects.
5. Queue-wide and device-wide idle waits on common paths. That is acceptable for bring-up, not for a scalable renderer.

## What I would change first

### Phase 1: correctness

1. Introduce explicit frame state so resize / out-of-date handling cannot submit invalid work.
2. Rework swapchain recreation to rebuild all dependent state correctly.
3. Make resource disposal idempotent and remove dependence on the global `VulkanContext`.
4. Tighten Vulkan call error handling in the command pool and upload helpers.

### Phase 2: architecture

1. Decide whether the engine is Vulkan-only for the near term or whether a real HAL is required now.
2. If HAL is required, move all resource creation behind backend factories.
3. Replace `Texture2D.bind()` and marker `RenderContext` with explicit frame/pass submission APIs.
4. Separate resource ownership from draw-binding descriptions.

### Phase 3: performance and scalability

1. Replace `queueWaitIdle` / `deviceWaitIdle` usage with fence-based upload and deferred destruction.
2. Add a proper pipeline cache keyed by render-pass/layout signature.
3. Add explicit transfer/upload infrastructure and device capability selection.

## Bottom line

The Vulkan backend is a promising base, but it is not yet engine-grade. The most serious problems are not shader math or syntax; they are lifecycle contracts, ownership, and the gap between the engine API and the actual implementation. If you fix those first, the renderer can grow cleanly. If you keep layering features on top of the current implicit-state model, the codebase will get expensive to change very quickly.
