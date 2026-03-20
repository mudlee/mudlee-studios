# 22. Complete File Reference

Every file in the project with a brief description.

## Core Engine — Framework (`hu.mudlee.core`)

| File | Description |
|------|-------------|
| `Game.java` | Abstract base class for applications. Owns the main loop, initializes all subsystems. |
| `GameModule.java` | Abstract pluggable extension that hooks into the game loop (update, draw, resize, dispose). |
| `GameTime.java` | Per-frame timing info: delta time, total time, running slowly flag. |
| `GraphicsDevice.java` | Public GPU facade. Provides `beginFrame()`, `present()`, viewport access. |
| `GraphicsDeviceManager.java` | Fluent builder for window/render configuration (resolution, vsync, backend). |
| `Screen.java` | Interface for game screens with lifecycle methods (show, update, draw, dispose). |
| `ScreenManager.java` | Stack-based screen navigator. Extends GameModule. Supports set/push/pop. |
| `Color.java` | RGBA color with predefined constants (WHITE, BLACK, RED, etc.). |
| `Rectangle.java` | Axis-aligned rectangle. |
| `Viewport.java` | Render viewport region (x, y, width, height). |
| `Time.java` | Global time tracking utility. |
| `Disposable.java` | Interface with `dispose()` method for resource cleanup. |

## Core Engine — Content (`hu.mudlee.core.content`)

| File | Description |
|------|-------------|
| `ContentManager.java` | Asset loader with caching. Registers type-specific loaders. |
| `ContentLoader.java` | Interface for asset loaders. Implement to add custom asset types. |

## Core Engine — IO (`hu.mudlee.core.io`)

| File | Description |
|------|-------------|
| `ResourceLoader.java` | Utility to load text files from the classpath. |

## Core Engine — Rendering (`hu.mudlee.core.render`)

| File | Description |
|------|-------------|
| `Renderer.java` | Singleton facade over GraphicsContext. Delegates all rendering operations. |
| `GraphicsContext.java` | Backend interface. Defines frame lifecycle, render passes, draw calls. |
| `RenderBackend.java` | Enum of available backends (currently VULKAN only). |
| `RenderBackendFactory.java` | Interface for creating backend-specific GPU objects (shaders, buffers, textures). |
| `SpriteBatch2D.java` | 2D sprite batcher. Batches up to 1000 sprites per flush. Dynamic vertex buffer. |
| `SpriteRenderPass.java` | Interface for sprite rendering (draw contract for ECS render systems). |
| `SpriteRenderCoordinator.java` | Delegates sprite draw calls to active SpriteBatch2D. |
| `MeshRenderCoordinator.java` | Coordinates 3D mesh rendering through the Renderer. |
| `VertexArray.java` | Abstract vertex format binding (groups VBOs + optional EBO). |
| `VertexBuffer.java` | Abstract GPU vertex buffer. Static or dynamic allocation. |
| `ElementBuffer.java` | Abstract GPU index buffer. |
| `VertexBufferLayout.java` | Describes vertex format: attributes, stride, input rate. |
| `VertexLayoutAttribute.java` | Single vertex attribute: location, component count, type, offset. |
| `VertexInputRate.java` | Enum: PER_VERTEX or PER_INSTANCE. |
| `Shader.java` | Abstract shader program. Factory methods delegate to backend. |
| `RenderTarget.java` | Abstract off-screen render surface. |
| `RenderPassOptions.java` | Record: color load action (CLEAR or LOAD). |
| `ColorLoadAction.java` | Enum: CLEAR or LOAD for render pass attachment. |

## Core Engine — Shader Types (`hu.mudlee.core.render.types`)

| File | Description |
|------|-------------|
| `ShaderConfig.java` | Record: depth test, blending, cull mode, model matrix flag. Presets for 2D/3D. |
| `ShaderTypes.java` | Enum of data types (FLOAT, INT) with Vulkan format constants. |
| `ShaderCullMode.java` | Enum: NONE, FRONT, BACK. |
| `ShaderProps.java` | Constants for uniform names (projection, view, model, color, texture). |
| `IndexType.java` | Enum for index buffer data type. |

## Core Engine — Vulkan Backend (`hu.mudlee.core.render.vulkan`)

| File | Description |
|------|-------------|
| `VulkanContext.java` | Core Vulkan graphics context. Manages frame loop, command recording, render passes, descriptors, pipeline cache. Singleton. |
| `VulkanInstance.java` | Creates VkInstance with extensions and validation layer debug messenger. |
| `VulkanDevice.java` | Selects physical GPU (scored by VRAM + type), creates logical device with graphics/present queues. |
| `VulkanSwapChain.java` | Swapchain creation/recreation, color image views, shared depth buffer, framebuffer cache. |
| `VulkanCommandPool.java` | Command pool + per-frame command buffers. FRAMES_IN_FLIGHT=2. Single-use command helpers. |
| `VulkanSyncObjects.java` | Fences and semaphores for frame synchronization. |
| `VulkanAllocator.java` | VMA (Vulkan Memory Allocator) wrapper for sub-allocated GPU memory. |
| `VulkanBuffer.java` | Low-level buffer: VkBuffer + VMA allocation. Device-local, staging, or dynamic strategies. |
| `VulkanVertexBuffer.java` | Vertex buffer: static (device-local via staging) or dynamic (per-frame host-visible). |
| `VulkanIndexBuffer.java` | Index buffer: always device-local, uploaded via staging. 32-bit indices. |
| `VulkanVertexArray.java` | Lightweight container grouping VBOs + optional EBO (no actual Vulkan object). |
| `VulkanShader.java` | Shader modules + deferred pipeline creation. Push constants for matrices. Pipeline cache by (layouts, renderPass). |
| `VulkanTexture2D.java` | GPU texture: image upload via staging, image view, sampler, pre-allocated descriptor set. |
| `VulkanRenderTarget.java` | Off-screen surface: color image as attachment + samplable texture. Layout transitions. Framebuffer cache. |
| `VulkanRenderPass.java` | Creates VkRenderPass from VulkanRenderPassSpec. |
| `VulkanRenderPassSpec.java` | Immutable record describing render pass configuration (format, load ops, layouts, depth). |
| `VulkanRenderBackendFactory.java` | Concrete factory creating all Vulkan-specific objects. |
| `VulkanMemoryUtil.java` | Memory layout helpers for descriptor sets and buffer copies. |
| `VulkanTextureBinding.java` | Interface providing `descriptorSetHandle()` for texture binding. |

## Core Engine — Textures (`hu.mudlee.core.render.texture`)

| File | Description |
|------|-------------|
| `Texture2D.java` | Abstract 2D texture. Factory methods for file and raw pixel creation. |
| `TextureLoader.java` | STB Image wrapper for loading PNG/JPG/BMP. |
| `TextureData.java` | Raw pixel data holder (width, height, ByteBuffer). |
| `TextureRegion.java` | Record: rectangular sub-area of a texture (UV coordinates). |
| `TextureAtlas.java` | Maps named regions to UV coordinates within a single texture. |
| `SpriteSheet2D.java` | Grid-based sprite extraction from a texture (column, row → TextureRegion). |

## Core Engine — Animation (`hu.mudlee.core.render.animation`)

| File | Description |
|------|-------------|
| `Animation2D.java` | Keyframe animation definition: frames array + frame duration. |
| `AnimationPlayer2D.java` | Playback state machine: manages named animations, play/stop/update. |
| `PlayMode.java` | Enum: ONCE or LOOPED playback. |

## Core Engine — Cameras (`hu.mudlee.core.render.camera`)

| File | Description |
|------|-------------|
| `Camera2D.java` | Abstract 2D camera: position, zoom, rotation, dirty flag. |
| `OrthographicCamera2D.java` | 2D orthographic projection (1 unit = 1 pixel). |
| `Camera3D.java` | Abstract 3D camera: position, Euler angles, basis vectors. Pitch clamped ±89°. |
| `PerspectiveCamera3D.java` | Perspective projection: FOV, aspect ratio, near/far. Matrix caching with dirty flags. |
| `FreeCameraController3D.java` | FPS-style camera controller: WASD + mouse + gamepad. |

## Core Engine — Meshes (`hu.mudlee.core.render.mesh`)

| File | Description |
|------|-------------|
| `Mesh3D.java` | Simple indexed 3D mesh container (VAO + VBO + EBO + index count). |
| `CubeMesh.java` | Generates a colored unit cube with per-face colors. |

## Core Engine — Font (`hu.mudlee.core.render.font`)

| File | Description |
|------|-------------|
| `BitmapFont.java` | Bitmap-based font rendering using glyph atlas. Draw and measure text. |

## Core Engine — ECS Framework (`hu.mudlee.core.ecs`)

| File | Description |
|------|-------------|
| `World.java` | Top-level ECS container. Owns EntityManager and SystemManager. |
| `Entity.java` | Record wrapping an integer ID. |
| `Component.java` | Marker interface for all component types. |
| `EntityManager.java` | Entity/component storage with dual-index and cached aspect queries. |
| `SystemManager.java` | Manages system collection, dispatches update/render. |
| `SystemBase.java` | Abstract base for all systems. Lifecycle: initialize → onStart → update → onDispose. |
| `EntityProcessingSystem.java` | Convenience base: auto-queries entities, calls process() for each match. |
| `RenderSystemBase.java` | Base for rendering systems (called during world.render()). |
| `Aspect.java` | Record defining required component types for entity queries. |
| `ComponentMapper.java` | Type-safe component accessor: get(entity), has(entity). |
| `ComponentMapperService.java` | Factory for ComponentMapper instances, injected into systems. |

## Core Engine — ECS Components (`hu.mudlee.core.ecs.component`)

| File | Description |
|------|-------------|
| `Transform2DComponent.java` | Position (Vector2f), rotation (float), scale (float). |
| `Sprite2DComponent.java` | Texture or TextureRegion + color tint. |
| `Animation2DComponent.java` | Holds AnimationPlayer2D for animated sprites. |
| `CameraComponent.java` | Holds a camera instance (2D or 3D). |

## Core Engine — ECS Systems (`hu.mudlee.core.ecs.system`)

| File | Description |
|------|-------------|
| `Transform2DPropagationSystem.java` | Propagates parent→child transforms in hierarchy. |
| `Animation2DSystem.java` | Updates AnimationPlayer2D on all entities with Animation2DComponent. |
| `SpriteRender2DSystem.java` | Renders sprites with transforms via SpriteRenderPass. |

## Core Engine — Input (`hu.mudlee.core.input`)

| File | Description |
|------|-------------|
| `InputSystem.java` | Internal hub: processes GLFW callbacks, polls gamepad, dispatches state. |
| `Keyboard.java` | Public keyboard API: isDown(), isPressed(), getState(). |
| `Mouse.java` | Public mouse API: position, delta, scroll, button state. |
| `Gamepad.java` | Public gamepad API: buttons, axes, connection state. |
| `Keys.java` | Enum mapping all GLFW key constants. |
| `MouseButton.java` | Enum: LEFT, RIGHT, MIDDLE, etc. |
| `GamepadButton.java` | Enum: A, B, X, Y, LB, RB, START, BACK, etc. |
| `GamepadAxis.java` | Enum: LEFT_X, LEFT_Y, RIGHT_X, RIGHT_Y, LT, RT. |
| `KeyboardState.java` | Snapshot record of keyboard state. |
| `MouseState.java` | Snapshot record: position, delta, scroll, buttons. |
| `GamepadState.java` | Snapshot record: buttons, axes. |
| `InputAction.java` | Named action with bindings and state machine (IDLE→STARTED→PERFORMED). |
| `InputBinding.java` | Interface binding physical input to action. |
| `InputActionMap.java` | Named collection of InputActions. |
| `InputActionContext.java` | Runtime context for action evaluation. |
| `ActionPhase.java` | Enum: IDLE, STARTED, PERFORMED, CANCELLED. |
| `ActionType.java` | Enum: BUTTON, FLOAT, VECTOR2. |

## Core Engine — Window (`hu.mudlee.core.window`)

| File | Description |
|------|-------------|
| `Window.java` | Singleton GLFW window wrapper. Creates window, dispatches events. |
| `WindowEventListener.java` | Interface: onWindowPrepared(), onWindowCreated(), onWindowResized(). |
| `ScreenPixelRatioHandler.java` | Detects HiDPI/Retina scale factor. |
| `CursorMode.java` | Enum: NORMAL, HIDDEN, DISABLED. |

## Core Engine — Settings (`hu.mudlee.core.settings`)

| File | Description |
|------|-------------|
| `WindowPreferences.java` | Builder for window configuration (title, size, vsync, fullscreen, AA). |
| `Antialiasing.java` | Enum for MSAA settings. |

## Core Engine — UI (`hu.mudlee.core.ui`)

| File | Description |
|------|-------------|
| `UIManager.java` | GameModule for UI overlay rendering. |
| `UICanvas.java` | Root container for UI objects. |
| `UIObject.java` | Base class for canvas-placed elements. |
| `UIComponent.java` | Base for interactive/visual UI elements. |
| `UITransform.java` | Position and size for UI elements. |
| `UIBatch.java` | Batched rendering for UI elements. |
| `DebugStatsComponent.java` | Real-time stats overlay (FPS, draw calls, memory). |
| `StatThreshold.java` | Defines warning thresholds for stats. |
| `WarningLevel.java` | Enum: NORMAL, WARNING, CRITICAL (color-coded). |

## Sandbox Application (`hu.mudlee.sandbox`)

| File | Description |
|------|-------------|
| `SandboxApplication.java` | Entry point. Configures engine: 1920x1080, Vulkan, ScreenManager + UIManager. |
| `CubeScene.java` | 3D demo: rotating colored cube with perspective camera and free-fly controls. |
| `PlayerScene.java` | 2D demo: animated sprite character with ECS, action-based input, orthographic camera. |
| `PlayerControlSystem.java` | ECS system processing player input → state transitions → animation selection. |
| `PlayerStateComponent.java` | Component tracking player state (IDLE/WALK/ATTACK/DIE) and direction. |

## Shaders (`resources/shaders/vulkan/`)

| File | Description |
|------|-------------|
| `2d/vert.glsl` | 2D vertex shader: push constant projection+view, position+color+uv vertex format. |
| `2d/frag.glsl` | 2D fragment shader: texture sampling × vertex color modulation. |
| `2d/vert.spv` | Pre-compiled SPIR-V of 2D vertex shader. |
| `2d/frag.spv` | Pre-compiled SPIR-V of 2D fragment shader. |
| `3d/vert.glsl` | 3D vertex shader: push constant projection+view+model, position+color vertex format. |
| `3d/frag.glsl` | 3D fragment shader: pass-through vertex color output. |

## Resources

| File | Description |
|------|-------------|
| `textures/mario.png` | Test texture (background sprite). |
| `textures/sprites/player.png` | Player sprite sheet (48×48 grid, animations). |
| `fonts/Inter.ttf` | Inter font for UI text rendering. |
| `simplelogger.properties` | SLF4J Simple logger configuration. |

## Build Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | Root build: Java 17, Spotless formatting, submodule config. |
| `core/build.gradle.kts` | Core module: java-library, LWJGL/JOML/SLF4J deps, resource paths. |
| `sandbox/build.gradle.kts` | Sandbox module: application + jlink, main class, JVM args. |
| `settings.gradle.kts` | Gradle settings: includes core and sandbox submodules. |
| `gradle.properties` | Shared Gradle properties. |
| `core/src/main/java/module-info.java` | JPMS module descriptor for hu.mudlee.core. |
| `sandbox/src/main/java/module-info.java` | JPMS module descriptor for hu.mudlee.sandbox. |
