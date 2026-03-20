# 3D Scene Implementation Plan

| Step | Area                    | Brief Description                                                                                 | Result                                                      |
|------|-------------------------|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| 1    | Architecture boundary   | Define the 3D path as a separate scene/render layer beside the existing 2D path.                  | 3D can be added without regressing 2D games.                |
| 2    | Camera                  | Add a dedicated 3D camera with perspective projection and view-matrix generation.                 | The scene has a real free-fly camera foundation.            |
| 3    | Input                   | Add camera/navigation bindings for keyboard, mouse, and gamepad, plus missing mouse-look support. | One input map can drive the same camera from all devices.   |
| 4    | Shader/pipeline         | Extend the render layer for 3D matrices and pipeline state.                                       | The backend can render solid 3D geometry correctly.         |
| 5    | Depth support           | Add depth attachment/state for the 3D pass.                                                       | The cube renders with correct hidden-surface removal.       |
| 6    | Mesh data               | Introduce a minimal generated mesh path for indexed vertex data.                                  | A cube can be built from code without model loading.        |
| 7    | 3D pass                 | Add a dedicated 3D render coordinator or scene renderer.                                          | 3D drawing lives outside the sprite-only abstractions.      |
| 8    | Sandbox demo            | Add a demo screen/scene that creates the cube and drives the camera.                              | There is a concrete place to verify the feature end to end. |
| 9    | Ordering and validation | Keep frame sequencing explicit: `3D -> 2D -> UI`, then verify controls and resize behavior.       | 3D and 2D can coexist cleanly in the engine.                |

## Constraints

- Keep the existing 2D sprite/ECS flow intact for 2D games.
- No lighting yet.
- No model loading yet.
- First milestone is a manually generated cube in the middle of the scene.
- Camera must support keyboard/mouse and gamepad.

## Step 1. Lock The Boundary Between 2D And 3D

Decide up front that 3D is a new path, not an expansion of the current sprite path.

- Leave the current 2D stack as-is: `SpriteBatch2D`, `SpriteRenderCoordinator`, `SpriteRenderPass`, `SpriteRender2DSystem`, `Camera2D`, and `CameraComponent`.
- Do not widen `World.render(SpriteRenderPass)` into a mixed 2D/3D abstraction yet. That would couple sprite systems to 3D requirements too early.
- Introduce new 3D-specific types beside the 2D ones, then compose them at the screen level.
- Target draw order should be `3D scene -> 2D scene -> UI`.
- Follow the same high-level separation MonoGame encourages: 2D stays `SpriteBatch`-like, while 3D uses explicit world/view/projection matrices and raw mesh draws.

Why this step matters: the current engine already has a generic low-level renderer, but its scene/ECS layer is intentionally 2D-shaped. Preserving that separation keeps 2D games simple.

## Step 2. Add A Dedicated 3D Camera

Create a small 3D camera package instead of trying to reuse the 2D camera types.

- Add a `Camera3D` or `PerspectiveCamera3D` with world position, yaw/pitch rotation, field of view, near/far planes, resize-aware aspect ratio handling, and computed `projection`, `view`, and optionally `viewProjection` matrices.
- Clamp pitch to avoid flipping.
- Keep the first implementation focused on a free camera. No target/orbit camera abstraction is needed yet.

Suggested outcome: a camera object that can answer `getProjectionMatrix()` and `getViewMatrix()` every frame and can be resized with the window.

## Step 3. Add Camera Input For Keyboard, Mouse, And Gamepad

Use the existing action system for movement and most look controls, then fill the one missing gap: relative mouse look.

- Create a dedicated input map for the 3D scene, for example `Move` (`VECTOR2`, bound to `WASD` and left stick), `Look` (`VECTOR2`, bound to right stick), `Rise/Fall` (for `Space` / `Ctrl` or shoulder buttons), `Sprint`, and optional `CaptureMouse` / `ReleaseMouse` actions.
- Add engine support for mouse delta or cursor-lock mode in the window/input layer.
- Prefer a small, explicit addition near `Window` and `InputSystem` rather than pushing raw GLFW calls into scene code.
- Add a public window API for cursor behavior, for example `Window.setCursorMode(...)`, and expose frame-relative mouse delta from the input layer instead of recomputing it ad hoc in scene code.
- Implement a `FreeCameraController3D` that reads the action map plus mouse delta and updates the camera every frame.
- Normalize move input and scale by `deltaTime`, move speed, look sensitivity, and gamepad deadzone.

Why this step matters: keyboard and gamepad movement are already close to supported, but mouselook needs cursor capture or per-frame delta to feel correct.

## Step 4. Extend Shader And Pipeline Support For 3D

The low-level renderer can already draw arbitrary vertex/index data, but the current shader abstraction is still 2D-biased.

- Add a minimal 3D shader pair under `resources/shaders/vulkan/3d/`.
- Extend shader uniform support so the 3D path can send at least projection, view, and model matrices.
- If you want the smallest change, support a single `uModel` in addition to the existing `uProjection` and `uView`.
- Add pipeline options or a 3D-specific shader path with depth testing enabled, optional back-face culling enabled, and blending disabled by default for opaque geometry.
- Be explicit about the push-constant budget: the current Vulkan path uses 128 bytes for `projection + view`, so adding `model` raises that to 192 bytes. Query the device limit and keep a fallback path ready, for example CPU-side `viewModel` composition or a small UBO.

This should stay minimal. No lighting uniforms, materials, or textures are needed for the first cube.

## Step 5. Add Depth Support To The 3D Pass

This is the most important renderer-level gap for even a simple cube.

- Add a depth attachment to the swapchain render pass, or add a dedicated depth-capable 3D render pass if that fits the current Vulkan design better.
- Create and recreate the depth image/view when the window or swapchain changes.
- Make depth format selection explicit and keep the depth image lifecycle tied to swapchain recreation so resize handling stays predictable.
- Enable depth testing and depth writes for the 3D pipeline.
- Keep the 2D/UI passes using color-only behavior after the 3D pass.

Expected result: when the cube rotates or the camera moves, hidden faces stay hidden correctly.

## Step 6. Add A Minimal Generated Mesh Path

Do not jump to model loading. Build the smallest useful mesh layer for procedurally defined geometry.

- Introduce a simple mesh/container type that owns a vertex buffer, index buffer, vertex array, and primitive/index count.
- Define a 3D vertex layout for the first milestone.
- Minimum: position plus color.
- Make the first cube concrete: `position(vec3) + color(vec4)`, `24` vertices, and `36` indices so each face can have its own solid color without sharing corner attributes.
- Optional: reserve UVs now only if that simplifies future growth.
- Add a helper that generates cube vertex/index data in code.
- Put the cube at the origin so the camera can orbit or fly around a known center.

This gives you a reusable path for other debug primitives later, not just the first cube.

## Step 7. Add A Dedicated 3D Render Pass Or Scene Renderer

Keep 3D rendering outside `SpriteRenderPass`.

- Add a `MeshRenderCoordinator`, `Scene3DRenderer`, or similarly named type that begins the 3D pass, binds the camera matrices, draws one or more meshes, and ends the 3D pass.
- For the first milestone, drawing one cube is enough. Design the API so multiple meshes can be added later.
- Keep per-object transforms simple: one model matrix per draw is enough.
- Prefer a small pipeline configuration object or equivalent flags rather than baking 3D state into the current 2D-oriented Vulkan shader path. The 2D default should remain `no depth + alpha blend`, while the 3D default becomes `depth test + back-face culling + opaque blend`.

The screen should be able to do something structurally like this:

```text
beginFrame()
draw3dScene()
draw2dScene()
drawUi()
present()
```

## Step 8. Build A Sandbox 3D Demo Screen

Add a dedicated demo screen rather than mixing the first 3D test into the existing 2D player scene.

- Create a new sandbox screen, for example `Sandbox3DScene` or `CubeScene3D`.
- In `show()`, create the camera, input map, cube mesh, and 3D renderer/pass object.
- In `update()`, update the free camera controller and optionally animate cube rotation so depth/camera movement are easier to verify.
- In `draw()`, begin the frame, draw the cube with the 3D renderer, and optionally layer 2D debug/UI on top.
- Hook the new screen into the sandbox app so it can be launched directly or swapped from the current sample scene.

Why this step matters: it gives a tight feedback loop for engine work without complicating the current 2D example.

## Step 9. Validate Integration And Preserve 2D Support

Before calling the feature done, verify that 3D was added without damaging the 2D path.

- Confirm existing 2D sandbox behavior still works unchanged.
- Verify resize updates the 3D camera aspect ratio and recreates depth resources correctly.
- Verify mouse capture/release behavior is predictable.
- Verify movement parity: keyboard + mouse can move/look smoothly, gamepad left stick moves, and gamepad right stick looks.
- Confirm UI still renders on top of the 3D scene.
- Add a short follow-up checklist for next milestones after the cube works: unlit material/shader cleanup, transform components for 3D entities, reusable debug primitives, and optional scene graph or ECS integration for 3D later.

## Suggested Implementation Order

1. Step 1: lock the boundary and decide the screen-level composition.
2. Step 2: add the 3D camera.
3. Step 3: add mouse delta/cursor capture and the free camera controller.
4. Step 4 and Step 5 together: add the 3D shader path and depth-enabled pipeline.
5. Step 6: add generated cube mesh support.
6. Step 7: add the dedicated 3D renderer/pass.
7. Step 8: wire the sandbox demo.
8. Step 9: validate `3D -> 2D -> UI` and confirm 2D regressions did not appear.

## Notes From The Current Codebase

- The low-level renderer is already generic enough to reuse for 3D meshes.
- The scene/ECS API is still sprite-specific, so forcing 3D into that API right now would create unnecessary churn.
- Gamepad support already exists through `InputAction` stick composites.
- Mouse look is the main missing input feature.
- Depth testing is the main missing render feature.
