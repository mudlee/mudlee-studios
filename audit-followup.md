# Vulkan Engine Follow-Up Audit

Date: 2026-03-18

Scope: follow-up work after implementing audit points 1 through 12 in `audit.md`.

## Status

All renderer follow-up items from the previous version of this document are now implemented.

## DONE

### 1. Explicit texture submission

- `Texture2D.bind()` was removed from the public API.
- Texture selection is now explicit in draw submission.
- Descriptor-set binding is owned by the Vulkan draw path instead of hidden global state.

### 2. Canonical frame lifecycle

- The compatibility wrappers that preserved the older call-order model were removed.
- The canonical public flow is now `beginFrame(...)`, explicit `beginRenderPass(..., RenderPassOptions)`, `endRenderPass()`, and `present(...)`.

### 3. Pass orchestration moved above batching

- `SpriteBatch2D` no longer owns render-pass begin/end.
- `SpriteRenderCoordinator` now owns pass lifecycle for scene and UI sprite rendering.

### 4. Explicit pass configuration

- Pass load behavior is now expressed with `RenderPassOptions` and `ColorLoadAction`.
- Backbuffer and render-target passes both resolve through the same Vulkan render-pass abstraction instead of ad hoc special cases.

### 5. Less Vulkan-shaped engine surface

- Engine-level texture submission is now API-neutral instead of relying on Vulkan-global descriptor state.
- The public render flow is expressed in terms of frame/pass concepts rather than backend-era wrapper calls.

### 6. Deferred destruction

- Vulkan resource destruction now goes through per-frame deferred release queues.
- Buffers, textures, shaders, and render-target GPU objects are retired after the relevant in-flight fence is safe to reuse instead of forcing immediate cleanup waits.

## Residual notes

These are not current correctness issues, but they are still worth keeping in mind:

- Upload command submission is still synchronous at creation time; the engine does not yet expose a higher-level async asset upload pipeline.
- The backend abstraction is still only exercised by the Vulkan implementation, so future render features should keep pressure-testing API neutrality.

## Bottom line

The OpenGL-era public patterns called out by the earlier follow-up are removed from the current renderer path. The remaining work is no longer cleanup of hidden render state; it is future scalability work.
