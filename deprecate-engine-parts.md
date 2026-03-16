# Deprecated Engine Parts — OpenGL Leftovers in the HAL

After removing the OpenGL backend, the following methods, parameters, and types remain in the
rendering HAL but are vestigial. They were designed around OpenGL's state-machine model and do not
map to Vulkan (or any modern explicit API). The rendering layer should stay backend-agnostic, but
these items add no value to any realistic backend and should be cleaned up.

---

## 1. `GraphicsContext.renderRaw()` — RenderMode & PolygonMode parameters

**Files:** `GraphicsContext.java:21-29`, `VulkanContext.java:315-328`

Both overloads accept `RenderMode` and `PolygonMode`, but VulkanContext **ignores both** and always
uses `VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST` + `VK_POLYGON_MODE_FILL`. The only caller
(`SpriteBatch2D:267`) always passes `TRIANGLES` and `FILL`.

In Vulkan, topology and polygon mode are baked into the pipeline at creation time — they cannot vary
per draw call. These parameters give the false impression that draw-time topology switching is
supported.

**Action:** Remove `RenderMode` and `PolygonMode` parameters from both `renderRaw()` overloads.

---

## 2. `GraphicsContext.setClearFlags(BufferBitTypes... flags)` — no-op

**Files:** `GraphicsContext.java:15`, `VulkanContext.java:244-246`

VulkanContext explicitly documents this as a no-op. Vulkan clearing is declarative
(`VK_ATTACHMENT_LOAD_OP_CLEAR` on the render pass), not imperative like `glClear()`. The only
caller (`GraphicsDevice`) always passes `BufferBitTypes.COLOR`.

**Action:** Remove the method. Clearing behaviour belongs in render pass configuration.

---

## 3. `GraphicsContext.setBlend(boolean, BlendFactor, BlendFactor)` — no-op

**Files:** `GraphicsContext.java:37`, `SpriteBatch2D.java:110,196`

VulkanContext has no implementation (inherits the default no-op). SpriteBatch2D calls it to
enable/disable alpha blending, but the Vulkan pipeline already hardcodes
`SRC_ALPHA / ONE_MINUS_SRC_ALPHA` blending at creation time.

In Vulkan, blend state is baked into `VkPipelineColorBlendAttachmentState` and cannot be toggled
per draw.

**Action:** Remove the method. Alpha blending is always on for sprite rendering.

---

## 4. `GraphicsContext.setViewport()` — no-op

**Files:** `GraphicsContext.java:35`

Default implementation is a no-op. Vulkan sets the viewport dynamically inside the render pass but
always to the full swapchain/render-target size. No partial-viewport support exists.

**Action:** Remove. Viewport management is internal to the render pass.

---

## 5. `GraphicsContext.setScissor()` — no-op

**Files:** `GraphicsContext.java:39`

Default implementation is a no-op. Never called from game code. Vulkan sets the scissor rect to
the full framebuffer inside the render pass.

**Action:** Remove. If scissor clipping is needed later (e.g. UI), redesign as a first-class
feature.

---

## 6. `Shader.bind()` / `Shader.unbind()` — no-ops

**Files:** `Shader.java:14-16`, `VulkanShader.java:130-137`

Both are explicit no-ops in VulkanShader. Pipeline binding happens inside
`VulkanContext.renderRaw()` via `vkCmdBindPipeline()` — not through user-facing bind/unbind calls.
No game code ever calls these.

**Action:** Remove from the abstract class.

---

## 7. `Shader.createUniform(String name)` — no-op

**Files:** `Shader.java:18`, `VulkanShader.java:140-142`

OpenGL requires a `glGetUniformLocation()` call before setting uniforms. Vulkan uses push constants
and descriptor sets — no named locations exist. SpriteBatch2D calls
`shader.createUniform("TEX_SAMPLER")` which is a no-op.

**Action:** Remove. Uniform setup should happen at pipeline/descriptor-set level.

---

## 8. `Shader.setUniform()` — unused overloads

**Files:** `Shader.java:22,24,26`

Only the `setUniform(String, Matrix4f)` overload (for projection/view matrices) is actually used.
The `Vector4f`, `float`, and `int` overloads are all no-ops in Vulkan:

- `setUniform(String, int)` — called for `"TEX_SAMPLER"` but meaningless (texture binding uses
  descriptor sets).
- `setUniform(String, float)` — never called.
- `setUniform(String, Vector4f)` — never called.

**Action:** Remove the three unused overloads and the `"TEX_SAMPLER"` calls in SpriteBatch2D.

---

## 9. `Texture2D.getNativeHandle()` — throws in Vulkan

**Files:** `Texture2D.java:24-25`, `VulkanTexture2D.java:91-94`

Returns an `int`, but Vulkan texture handles are 64-bit. VulkanTexture2D throws
`UnsupportedOperationException`. Never called from game code.

**Action:** Remove. The public API should not expose backend-specific handles.

---

## 10. `Texture2D.unBind()` — no-op

**Files:** `Texture2D.java:29`, `VulkanTexture2D.java:102-105`

Explicit no-op in Vulkan. Descriptor sets are replaced, not unbound. Never called from game code
(only `bind()` is used).

**Action:** Remove.

---

## 11. `RenderTarget.getNativeHandle()` — throws in Vulkan

**Files:** `RenderTarget.java`, `VulkanRenderTarget.java:372-374`

Same issue as `Texture2D.getNativeHandle()` — `int` return type doesn't fit Vulkan's 64-bit
handles, so it throws `UnsupportedOperationException`.

**Action:** Remove.

---

## 12. `VertexBuffer.update(ByteBuffer, int)` — dead code

**Files:** `VertexBuffer.java:19-21`

Throws `UnsupportedOperationException`. SpriteBatch2D only calls `update(float[], int)`. The byte
buffer overload was added for flexibility that was never needed.

**Action:** Remove.

---

## 13. `ElementBuffer.update(ByteBuffer, int)` — dead code

**Files:** `ElementBuffer.java:18-20`

Throws `UnsupportedOperationException`. Index buffers are static (triangle fan pattern) and never
need runtime updates.

**Action:** Remove.

---

## 14. `BufferUsage` enum — dead code

**Files:** `BufferUsage.java`

Enum with `STATIC_DRAW`, `STREAM_DRAW`, `DYNAMIC_DRAW`. Not referenced by any factory method,
constructor, or game code. These are OpenGL usage hints (`glBufferData(... usage)`) that have no
Vulkan equivalent.

**Action:** Delete the file.

---

## 15. `VertexLayoutInstancedAttribute` — dead code

**Files:** `VertexLayoutInstancedAttribute.java`

Never instantiated anywhere. VulkanVertexArray checks for its presence but no code creates one.
Instanced rendering is not implemented.

**Action:** Delete the file. Redesign instancing as a proper feature when needed.

---

## 16. `BufferBitTypes` enum — dead code after `setClearFlags()` removal

**Files:** `BufferBitTypes.java`

Only used as the parameter type for `GraphicsContext.setClearFlags()`. Once that method is removed,
this enum has no remaining callers.

**Action:** Delete after removing `setClearFlags()`.

---

## 17. `RenderMode` enum — dead code after `renderRaw()` cleanup

**Files:** `RenderMode.java`

Only used as a parameter type in `GraphicsContext.renderRaw()`. Once those parameters are removed,
this enum has no remaining callers.

**Action:** Delete after cleaning up `renderRaw()`.

---

## 18. `PolygonMode` enum — dead code after `renderRaw()` cleanup

**Files:** `PolygonMode.java`

Same situation as `RenderMode`. Only used in `renderRaw()` signatures.

**Action:** Delete after cleaning up `renderRaw()`.
