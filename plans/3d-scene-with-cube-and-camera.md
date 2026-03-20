# Plan: 3D Scene with Cube and Camera Controls

## Summary

Add a 3D rendering pipeline to the engine (alongside the existing 2D pipeline) with a perspective camera, vertex-generated cube, and full keyboard/mouse + gamepad camera controls. No lighting, no model loading.

## Steps Overview

| # | Step                 | Scope                                                                  | Description                                                                        |
|---|----------------------|------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| 1 | 3D Shaders           | `resources/shaders/vulkan/3d/`                                         | New vertex/fragment shaders for 3D geometry with per-vertex color (no texture)     |
| 2 | Depth Buffer Support | `VulkanContext`, `VulkanSwapChain`, `VulkanRenderPass`, `VulkanShader` | Add depth attachment to render pass and enable depth testing in the pipeline       |
| 3 | Camera3D             | `core/.../render/camera/`                                              | Perspective camera with position, yaw, pitch, projection and view matrices         |
| 4 | CubeMesh Utility     | `core/.../render/`                                                     | Static helper that generates cube vertices (position + color) and indices          |
| 5 | 3D Pipeline Toggle   | `VulkanShader`                                                         | Allow shaders to opt into back-face culling and depth testing (3D pipeline config) |
| 6 | 3D Sandbox Scene     | `sandbox/`                                                             | New `CubeScene` screen that wires everything together                              |
| 7 | Camera Controller    | `sandbox/` or `core/.../render/camera/`                                | Orbital/FPS camera controller driven by InputActions (keyboard/mouse + gamepad)    |

---

## Detailed Steps

### Step 1: 3D Shaders

**Files to create:**
- `resources/shaders/vulkan/3d/vert.glsl`
- `resources/shaders/vulkan/3d/frag.glsl`
- Compiled `.spv` files for both

**Details:**

The existing 2D shaders (`vulkan/2d/`) use a vertex layout of `position(vec3) + color(vec4) + texCoords(vec2)` and sample a texture in the fragment shader. For the 3D cube we need a simpler shader that only uses vertex color, no texture sampling.

Vertex shader:
- Same push constant block as 2D (mat4 projection + mat4 view — 128 bytes, already the push constant layout in `VulkanShader`)
- Add a `uniform mat4 model` via a **new push constant** or extend the existing push constant block. However, the current push constant budget is 128 bytes (2 × mat4). Vulkan guarantees at least 128 bytes. Options:
  - **Option A (recommended):** Extend push constants to 192 bytes (3 × mat4: projection + view + model). The Vulkan spec guarantees `maxPushConstantsSize >= 128`, but virtually all desktop GPUs support 256. We can query `VkPhysicalDeviceLimits.maxPushConstantsSize` and assert ≥ 192.
  - **Option B:** Pre-multiply model × view on the CPU and send as a single `viewModel` matrix. Keeps push constants at 128 bytes but loses separate model matrix.
  - **Option C:** Use a UBO for model matrix. More complex, unnecessary for now.
  - Decision: Start with **Option A** (3 push constants). Update `VulkanShader` to support 192-byte push constant range.

Fragment shader:
- Simply pass through the interpolated vertex color. No texture sampler needed.
- Output: `outColor = fragColor;`

**Compile shaders** with `glslc` to `.spv` (can be done manually or via a Gradle task).

---

### Step 2: Depth Buffer Support

**Files to modify:**
- `VulkanSwapChain.java` — Create a depth image + image view (format: `VK_FORMAT_D32_SFLOAT` or `VK_FORMAT_D24_UNORM_S8_UINT`)
- `VulkanRenderPass.java` / `VulkanRenderPassSpec.java` — Add depth attachment to the render pass when 3D rendering is active
- `VulkanContext.java` — Attach depth image view to framebuffers
- `VulkanShader.java` — Add `VkPipelineDepthStencilStateCreateInfo` to the graphics pipeline

**Details:**

Currently the pipeline has **no depth testing** — the `VkGraphicsPipelineCreateInfo` at `VulkanShader.java:302` has no `pDepthStencilState`, and the render pass has no depth attachment. This is correct for 2D sprite rendering (Z-order is handled by draw order), but 3D requires hardware depth testing.

Changes needed:
1. **Depth image creation** in `VulkanSwapChain` (or a new `VulkanDepthBuffer` helper):
   - Find a supported depth format (`VK_FORMAT_D32_SFLOAT` preferred)
   - Create `VkImage` with `VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT`
   - Create `VkImageView` with `VK_IMAGE_ASPECT_DEPTH_BIT`
   - Transition layout to `VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL`
   - Recreate on swapchain resize

2. **Render pass modification:**
   - Add a second attachment description for the depth format
   - Add `VkAttachmentReference` for depth in the subpass
   - The render pass needs a depth attachment for 3D but the 2D render pass should remain as-is. Options:
     - **Option A (recommended):** Create the depth attachment always but only enable depth testing in the pipeline state for 3D shaders. Depth writes from 2D will be harmless if the depth buffer is cleared each frame.
     - **Option B:** Have separate render passes for 2D and 3D. More complex, only needed if mixing 2D and 3D in the same frame becomes a requirement later.

3. **Pipeline depth stencil state** in `VulkanShader.createGraphicsPipeline()`:
   - Add `VkPipelineDepthStencilStateCreateInfo` with `depthTestEnable(true)`, `depthWriteEnable(true)`, `depthCompareOp(VK_COMPARE_OP_LESS)`
   - For 2D shaders this can be disabled (or set to always-pass) — see Step 5

---

### Step 3: Camera3D (Perspective Camera)

**Files to create:**
- `core/.../render/camera/Camera3D.java` — Abstract base class for 3D cameras (mirrors `Camera2D` pattern)
- `core/.../render/camera/PerspectiveCamera3D.java` — Concrete implementation

**Details:**

Following the existing pattern where `Camera2D` is abstract and `OrthographicCamera2D` is concrete:

`Camera3D` (abstract):
- Fields: `Vector3f position`, `float yaw`, `float pitch`, `boolean dirty`
- Abstract methods: `getProjectionMatrix()`, `getViewMatrix()`
- Dirty flag pattern (same as `Camera2D`)
- Helper: `getForward()`, `getRight()`, `getUp()` — derived from yaw/pitch

`PerspectiveCamera3D`:
- Additional fields: `float fovY` (default 45°), `float nearPlane` (0.1), `float farPlane` (1000), `float aspectRatio`
- `getProjectionMatrix()` — `Matrix4f.perspective(fovY, aspectRatio, near, far)`. Recomputed on aspect ratio change or dirty.
- `getViewMatrix()` — `Matrix4f.lookAt(position, position + forward, up)`. Recomputed on position/rotation change.
- Handle window resize → update aspect ratio

**ECS integration:**
- The existing `CameraComponent` holds a `Camera2D`. For 3D we need either:
  - **Option A:** A new `Camera3DComponent` that holds a `Camera3D`. Clean separation, 2D games never see 3D types.
  - **Option B:** Make `CameraComponent` generic or use a common `Camera` interface.
  - Decision: **Option A** — keeps 2D and 3D cleanly separated per the user's requirement.

---

### Step 4: CubeMesh Utility

**Files to create:**
- `core/.../render/mesh/CubeMesh.java` (or `core/.../render/geometry/CubeMesh.java`)

**Details:**

A static utility that produces the vertex and index data for a unit cube centered at the origin (−0.5 to +0.5 on each axis).

Vertex layout for 3D colored geometry: `position(vec3) + color(vec4)` = 7 floats per vertex, 28 bytes stride.

The cube has 6 faces × 4 vertices = 24 vertices (duplicated at corners for distinct face colors), and 6 faces × 2 triangles × 3 indices = 36 indices.

Each face gets a distinct color so the cube is visually distinguishable without lighting:
- Front: red, Back: cyan, Left: green, Right: magenta, Top: blue, Bottom: yellow

The method returns a record or pair of `float[]` vertices and `int[]` indices that can be directly passed to `VertexBuffer.create()` and `ElementBuffer.create()`.

Also define the `VertexBufferLayout` for 3D colored geometry (position vec3 at location 0, color vec4 at location 1).

---

### Step 5: 3D Pipeline Configuration

**Files to modify:**
- `VulkanShader.java` — `createGraphicsPipeline()` method

**Details:**

Currently the pipeline is hardcoded for 2D sprites: no depth test, no back-face culling (`VK_CULL_MODE_NONE`), alpha blending enabled. For 3D we need different pipeline state.

Approach: Add a **pipeline configuration** concept. This could be:
- A `PipelineConfig` record/class passed when creating a `Shader`, containing: `boolean depthTest`, `int cullMode`, `boolean alphaBlend`
- Or shader-level flags set before pipeline creation

The `Shader.create()` factory method could accept an optional config:
```java
Shader.create("vulkan/3d/vert", "vulkan/3d/frag", PipelineConfig.DEFAULT_3D)
```

Where `PipelineConfig.DEFAULT_3D` enables:
- Depth testing + depth write
- Back-face culling (`VK_CULL_MODE_BACK_BIT`)
- No alpha blending (opaque geometry)

And the existing 2D default remains:
- No depth testing
- No culling
- Alpha blending enabled

This keeps both pipelines working correctly without interfering with each other.

---

### Step 6: 3D Sandbox Scene

**Files to create:**
- `sandbox/.../CubeScene.java`

**Files to modify:**
- `sandbox/.../SandboxApplication.java` — Switch to `CubeScene` (or add a scene switcher)

**Details:**

`CubeScene` implements `Screen` and demonstrates the 3D pipeline:

`show()`:
1. Create a `PerspectiveCamera3D` with default FOV, near/far planes
2. Position camera at `(0, 0, 3)` looking at origin
3. Create cube geometry using `CubeMesh`:
   - `VertexBuffer.create(vertices, layout3D)`
   - `ElementBuffer.create(indices)`
   - `VertexArray` binding them together
4. Create 3D shader: `Shader.create("vulkan/3d/vert", "vulkan/3d/frag", PipelineConfig.DEFAULT_3D)`
5. Set up input actions for camera control (see Step 7)

`draw(gameTime)`:
1. Set shader uniforms: projection matrix, view matrix (and model matrix if using push constants)
2. `Renderer.renderRaw(vao, shader)` — draw the cube

`update(gameTime)`:
1. Process camera controller input (update camera position/rotation)

`resize(w, h)`:
1. Update camera aspect ratio

`dispose()`:
1. Dispose shader, VAO, VBO, EBO

---

### Step 7: Camera Controller with Input Actions

**Files to create:**
- `core/.../render/camera/PerspectiveCameraController.java` (or in sandbox if we want to keep it as a demo)

**Details:**

An orbital/FPS-style camera controller that reads input and updates a `PerspectiveCamera3D`. Uses the existing `InputAction` and `InputActionMap` system.

**Input bindings:**

| Action | Type | Keyboard/Mouse | Gamepad |
|--------|------|----------------|---------|
| Move (forward/back/left/right) | VECTOR2 | WASD composite | Left stick (LEFT_X, LEFT_Y) |
| Look (yaw/pitch) | VECTOR2 | Mouse delta (requires capturing mouse) | Right stick (RIGHT_X, RIGHT_Y) |
| Move Up | BUTTON | SPACE | Right bumper |
| Move Down | BUTTON | LEFT_SHIFT | Left bumper |
| Toggle Mouse Capture | BUTTON | TAB or right-click | — |

**Mouse look implementation:**
- When mouse is captured (`glfwSetInputMode(GLFW_CURSOR, GLFW_CURSOR_DISABLED)`), mouse delta drives yaw/pitch
- Need to track previous mouse position and compute delta each frame
- Sensitivity multiplier (configurable)
- Pitch clamped to ±89° to avoid gimbal lock at poles

**Gamepad look:**
- Right stick X → yaw, right stick Y → pitch
- Apply sensitivity and delta time scaling
- Deadzone is already handled by `InputSystem` (0.15f)

**Movement:**
- Forward/back along camera's forward vector
- Left/right along camera's right vector
- Up/down along world Y axis
- Speed multiplier × delta time

**Note on mouse capture:** The engine's `Window` class currently doesn't expose cursor mode toggling. We'll need to add a method like `Window.setCursorMode(CursorMode)` with values `NORMAL`, `HIDDEN`, `DISABLED` (maps to GLFW cursor modes). Also need raw mouse delta — currently `MouseState` gives absolute position. We need to compute delta from frame-to-frame position changes, or add `deltaX()`/`deltaY()` to `MouseState` / `InputSystem`.

---

## Dependencies Between Steps

```
Step 1 (Shaders) ─────────────────────────┐
Step 2 (Depth Buffer) ────────────────────┤
Step 3 (Camera3D) ────────────────────────┤
Step 4 (CubeMesh) ────────────────────────┼──→ Step 6 (Scene) ──→ Step 7 (Controller)
Step 5 (Pipeline Config) ─────────────────┘
```

Steps 1–5 can be worked on in parallel. Step 6 integrates them all. Step 7 builds on the scene.

## Key Risks & Decisions

1. **Push constant size for model matrix:** Going from 128 to 192 bytes. Virtually all desktop GPUs support 256 bytes, but we should query the limit at startup and log a warning/error if insufficient. Alternatively, pre-multiply model × view on CPU.

2. **Depth buffer always present vs. on-demand:** Creating the depth buffer always (even for 2D) simplifies the render pass setup. The memory overhead is one extra image the size of the swapchain. Acceptable trade-off.

3. **Mouse delta for camera look:** The current `MouseState` tracks absolute position. We need to either:
   - Add delta tracking to `InputSystem` (cleaner, engine-level)
   - Compute delta in the controller (simpler, sandbox-level)
   - Recommendation: Add to `InputSystem` since FPS camera control is a fundamental engine feature.

4. **Cursor capture API:** `Window` needs a `setCursorMode()` method. Small addition, no architectural risk.

5. **2D/3D coexistence:** By keeping Camera2D/Camera3D as separate hierarchies and using pipeline config per shader, 2D and 3D games remain cleanly separated. A game can use either or both.
