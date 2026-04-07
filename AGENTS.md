# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## MOST IMPORTANT THINGS

- **No co-author attribution** — never add `Co-Authored-By:` lines to commit messages.
- **Always ask before committing** — never create a git commit without explicit user approval.

## Agent Role & Expertise

- Lead Java Architect: You are a senior Java developer specializing in high-performance systems. You adhere strictly to Clean Code principles, SOLID design, and modern Java best practices (Java 17/21+).
- Graphics Engineering Specialist: You possess expert-level knowledge of low-level graphics APIs, specifically Vulkan 1.3. You understand memory barriers, pipeline states, command buffers, and descriptor sets.

## Project Context & Primary Objectives

The current project is a Java-based game engine utilizing LWJGL (Lightweight Java Game Library). It uses a Vulkan rendering backend.

### Primary Objective

Create a basic 2D and 3D rendering engine like LibGDX and MonoGame.

**Key Engine Guidelines:**

- The engine's structure and API should be similar what MonoGame has (in Java style). See https://docs.monogame.net/api/index.html and https://www.monogameextended.net/docs/about/introduction/.
- See how MonoGame is used under this repository: https://github.com/MonoGame/MonoGame.Samples
- For Vulkan best practices always use https://vulkan-tutorial.com/ and https://www.vulkan.org/learn
- Resource Management: Vulkan's explicit memory allocation and synchronization via VMA (Vulkan Memory Allocator).
- Performance: Focus on reducing CPU overhead by utilizing Vulkan's multi-threaded command buffer recording.
- Code Integrity: Maintain strictly typed Java code. Avoid "C-style" Java; use objects effectively while staying mindful of garbage collection (GC) pressure in the render loop (e.g., utilize object pooling or direct buffers).

## Build & Run Commands

```bash
# Run the sandbox application
./gradlew run

# Build all modules
./gradlew build

# Create a jlink-packaged runtime image
./gradlew jlink

# Run the packaged application (after jlink)
./sandbox/build/image/bin/sandbox-app
```

There are no automated tests in this project. The sandbox module serves as the manual testing environment.

## Project Structure

Two Gradle submodules:

- **`core/`** — Game engine library (`java-library`). Contains the rendering system, ECS, screen management, input, window handling, and camera implementations.
- **`sandbox/`** — Example application (`application` + `jlink`). Entry point: `hu.mudlee.sandbox.SandboxApplication`. Used to test engine features.
- **`resources/`** — Shared assets (GLSL/SPIR-V shaders, textures, logging config) included in core's resource source set.
- **`docs/`** — 23 comprehensive markdown documents covering every subsystem (architecture, Vulkan backend, ECS, screens, input, content management, UI, etc.). Consult these for in-depth design details.

Both modules use Java Platform Module System (`module-info.java`).

## Architecture

### Game Loop (`Game.java`)

`Game` is an abstract class that owns the main loop. Users subclass it to create their application.
1. Creates the `Window` (GLFW 3.4.0) and `GraphicsDevice` (Vulkan backend)
2. `GraphicsDeviceManager` configures window size, title, vSync, fullscreen, and preferred backend
3. Lifecycle: `initialize()` → `loadContent()` → game loop → `unloadContent()`
4. Each frame: `InputSystem.update()` → poll events → `modules.update(gameTime)` → `modules.draw(gameTime)` → `graphicsDevice.present()`
5. Target: 60 FPS, max delta clamped at 100ms

### GameModule System

`GameModule` is an abstract base class for pluggable modules with `update(GameTime)`, `draw(GameTime)`, `resize()`, and `dispose()` lifecycle methods. Modules are added/removed via `Game.addModule()` / `Game.removeModule()` (deferred to avoid mid-loop mutations). `ScreenManager` and `UIManager` are both GameModules.

### Screen System

`Screen` is an interface with `show()`, `resume()`, `update(GameTime)`, `draw(GameTime)`, `resize()`, `hide()`, and `dispose()`. `ScreenManager` (a `GameModule`, also a singleton) provides stack-based screen management with `set()` (replace), `push()` (overlay), and `pop()` (resume previous). Transitions are deferred to prevent mid-callback mutations.

### Rendering

- **`GraphicsDevice`** — Public GPU facade with `beginFrame(Color)`, `present(float)`, `getViewport()`, `getBackend()`. Delegates to `Renderer`.
- **`Renderer`** — Singleton facade over `RenderBackend` (implemented by Vulkan backend via `VulkanRenderBackendFactory`).
- **`SpriteBatch2D`** — Batched 2D sprite rendering (max 1000 sprites), batches by texture for single GPU draw per texture. Usage: `begin(projection, view)` → `draw(texture, position, color)` → `end()`.
- **3D Pipeline** — `Mesh3D` (indexed mesh container), `CubeMesh` (unit cube helper), `MeshRenderCoordinator`, `PerspectiveCamera3D`, depth buffer support.
- **`ShaderConfig`** — Presets: `default2D()` vs `default3D()` with different depth/blending/culling settings.
- Shaders live under `resources/shaders/vulkan/` — `2d/` for the 2D pipeline, `3d/` for the 3D pipeline (GLSL sources + compiled SPIR-V).
- All renderable objects implement `Disposable` and must be explicitly cleaned up.

### Vulkan Backend

Key classes under `core/.../render/vulkan/`:
- **`VulkanContext`** — Singleton Vulkan context
- **`VulkanDevice`** — Logical device management
- **`VulkanSwapChain`** — Presentation layer
- **`VulkanCommandPool`** — Command buffer allocation
- **`VulkanSyncObjects`** — Semaphore/fence management
- **`VulkanAllocator`** — VMA memory allocator wrapper
- **`VulkanShader`** — Shader/pipeline management
- **`VulkanTexture2D`**, **`VulkanBuffer`**, **`VulkanVertexBuffer`**, **`VulkanIndexBuffer`**, **`VulkanVertexArray`**, **`VulkanRenderPass`**, **`VulkanRenderTarget`**

### ECS (`ecs/`)

Custom in-house Entity-Component-System framework. Core classes: `World`, `Entity` (record), `Component` (marker interface), `SystemBase`, `RenderSystemBase`, `EntityManager`, `ComponentMapper`, `Aspect`.
- Components: `Transform2DComponent`, `Sprite2DComponent`, `Animation2DComponent`, `CameraComponent`
- Systems: `SpriteRender2DSystem`, `Transform2DPropagationSystem`, `Animation2DSystem`

### Cameras

- **2D:** `Camera2D` (abstract) → `OrthographicCamera2D`. Position, zoom, rotation with dirty-flag caching.
- **3D:** `Camera3D` (abstract) → `PerspectiveCamera3D`. FOV 45°, near 0.1, far 1000. Yaw/pitch with cached projection/view matrices.
- **`FreeCameraController3D`** — Interactive 3D camera control (keyboard + mouse).

### Input System

Action-based input (Unity-style): `InputActionMap` (named group) → `InputAction` (with `ActionType`: BUTTON/VALUE/VECTOR2) → `InputBinding`. Supports keyboard, mouse, and gamepad with state snapshots. Fluent API for action creation. Callbacks: `onStarted`, `onPerformed`, `onCanceled`.

### Content Management

`ContentManager` provides generic type-safe asset loading and caching. Default loaders for `Texture2D`, `BitmapFont`, and `TextureAtlas`. Custom loaders registered via `registerLoader(Class<T>, ContentLoader<T>)`.

### Font Rendering

`BitmapFont` rasterizes TrueType fonts via STB into a 512×512 GPU texture atlas. Supports ASCII 32–127. Loaded through `ContentManager` with `"path.ttf@size"` syntax.

### UI System

`UIManager` (a `GameModule`) owns `UICanvas`, `UIBatch`, and a default `BitmapFont`. UI hierarchy: `UICanvas` → `UIObject` → `UIComponent`. Built-in: `DebugStatsComponent`.

### Key Singletons

`Window`, `Renderer`, `ScreenManager`, `VulkanContext` — follow the static-factory/singleton pattern. `Time` and `InputSystem` are static utility classes. `Game` and `GraphicsDevice` are instantiated once but are not singletons.

## Code Style Rules

These rules are enforced project-wide and must be followed in all new and modified code.

### Formatting

Code is formatted with **palantir-java-format** (120 character line length) via the Spotless Gradle plugin:

```bash
./gradlew spotlessApply   # format in-place
./gradlew spotlessCheck   # verify without modifying
```

### Comments

- Do **not** add section-divider banner comments (e.g. `// --- Whatever comes ---`)
- Only add comments to methods when the method itself is not self explanatory

### Mandatory Curly Braces

Every `if`, `else`, `for`, `while`, and `do` body **must** use curly braces, even for single-line bodies.

```java
// Wrong
if (condition) return;
for (int i = 0; i < n; i++) doSomething();

// Correct
if (condition) { return; }
for (int i = 0; i < n; i++) { doSomething(); }
```

### Class Member Ordering

Follow the standard Java class member ordering convention defined in the
[Google Java Style Guide §3.4.2](https://google.github.io/styleguide/javaguide.html#s3.4.2-class-member-ordering).
Within each section, `static` members come before instance members, and `public` before `private`.

```
1. Static constants      (static final fields)
2. Static fields
3. Instance fields
4. Constructors
5. Public / package / protected methods
6. Private methods
7. Inner classes / interfaces / enums
```

Example of correct ordering:

```java
public class Foo {
    private static final int MAX = 100;    // static constant
    private static Foo instance;           // static field
    private int value;                     // instance field

    private Foo() {}                       // constructor

    public static Foo get() { ... }        // public methods
    public void doSomething() { ... }

    private void helper() { ... }          // private methods
}
```

Do **not** add section-divider banner comments (e.g. `// --- Static fields ---`). The ordering itself communicates structure.

### Local Variable Type Inference (`var`)

Use `var` for all local variable declarations where the type is clear from the right-hand side.

```java
// Wrong
VkCommandBuffer cmdBuf = commandPool.beginSingleUse(stack);
LongBuffer pImage = stack.mallocLong(1);

// Correct
var cmdBuf = commandPool.beginSingleUse(stack);
var pImage = stack.mallocLong(1);
```

Do **not** use `var` for: fields, method parameters, return types, or cases where the inferred type would be ambiguous (e.g. a bare numeric literal like `var x = 0` when the intent is `long`).

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| LWJGL | 3.4.0 | Vulkan, GLFW, STB, VMA, shaderc bindings |
| JOML | 1.10.8 | Vector/matrix math |
| SLF4J Simple | 2.0.17 | Logging |

Key LWJGL modules: `lwjgl-vulkan`, `lwjgl-vma` (Vulkan Memory Allocator), `lwjgl-shaderc` (runtime shader compilation), `lwjgl-glfw`, `lwjgl-stb`.

LWJGL natives are resolved automatically at build time based on OS/arch.

## Platform Notes

- **macOS:** `-XstartOnFirstThread` is added automatically by the sandbox build config.
- **High-DPI / Retina:** Handled via `ScreenPixelRatioHandler`.
- LWJGL debug loader is enabled by default (`-Dorg.lwjgl.util.Debug=true`).
