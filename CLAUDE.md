# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## MOST IMPORTANT THINGS

- **No co-author attribution** — never add `Co-Authored-By:` lines to commit messages.
- **Always ask before committing** — never create a git commit without explicit user approval.

## Agent Role & Expertise

- Lead Java Architect: You are a senior Java developer specializing in high-performance systems. You adhere strictly to Clean Code principles, SOLID design, and modern Java best practices (Java 17/21+).
- Graphics Engineering Specialist: You possess expert-level knowledge of low-level graphics APIs, specifically OpenGL 4.5+ and Vulkan 1.3. You understand memory barriers, pipeline states, command buffers, and descriptor sets.

## Project Context & Primary Objectives

The current project is a Java-based game engine utilizing LWJGL (Lightweight Java Game Library). It has an OpenGL and Vulkan rendering backend.

### Primary Objective

Create a basic 2D and 3D rendering engine like LibGDX and MonoGame.

**Key Engine Guidelines:**

- The engine's structure and API should be similar what MonoGame has (in Java style). See https://docs.monogame.net/api/index.html and https://www.monogameextended.net/docs/about/introduction/.
- See how MonoGame is used under this repository: https://github.com/MonoGame/MonoGame.Samples
- For Vulkan best practices always use https://vulkan-tutorial.com/ and https://www.vulkan.org/learn
- Abstraction: Design a hardware abstraction layer (HAL) that allows the engine to eventually toggle between APIs. So hiding the rendering implementation (opengl or vulkan) behind an API.
- Resource Management: Move from OpenGL's implicit state management to Vulkan's explicit memory allocation and synchronization.
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

- **`core/`** — Game engine library (`java-library`). Contains the rendering system, ECS, scene management, input, window handling, and camera implementations.
- **`sandbox/`** — Example application (`application` + `jlink`). Entry point: `hu.mudlee.sandbox.SandboxApplication`. Used to test engine features.
- **`resources/`** — Shared assets (GLSL shaders, textures, logging config) included in core's resource source set.

Both modules use Java Platform Module System (`module-info.java`).

## Architecture

### Game Loop (`Application.java`)

`Application` is a singleton that owns the main loop:
1. Creates the `Window` (GLFW 3.4.0)
2. Initializes the `Renderer` (OpenGL 4.1 core profile, forward-compatible)
3. Delegates to `SceneManager` to set and drive the active `Scene`
4. Each frame: poll events → clear → `SceneManager.onUpdate(dt)` → `ECS.update(dt)` → swap buffers

### Scene System

`Scene` is an interface with `start()`, `update(dt)`, `resize(w,h)`, and `dispose()`. `SceneManager` handles transitions and calls `dispose()` on the outgoing scene automatically.

### Rendering

- **`Renderer`** — Facade over `GraphicsContext` (implemented by `OpenGLGraphicsContext`).
- OpenGL objects are wrapped: `OpenGLVertexArray` (VAO), `OpenGLVertexBuffer` (VBO), `OpenGLElementBuffer` (EBO), `OpenGLShader`, `OpenGLTexture2D`.
- Textures are loaded via STB (`TextureLoader`).
- Shaders live under `resources/shaders/` — `2d/` for the 2D pipeline, `raw/` for colored/non-colored geometry.
- All renderable objects implement `Disposable` and must be explicitly cleaned up.

### ECS (`ecs/`)

Uses the **Ashley** ECS framework (Badlogic Games). Components (`ShaderComponent`, `VertexArrayComponent`, `RawRenderSettingsComponent`) are attached to entities. `RawRenderableSystem` processes them each frame via `ECS.update(dt)`.

### Cameras

`Camera` → `AbstractCamera` → `Camera2D` / `OrthoCamera`. Cameras provide projection and view matrices passed to shaders as uniforms (`ShaderProps`).

### Key Singletons

`Application`, `Window`, `Renderer`, `SceneManager`, `ECS`, `Time` — all follow the static-factory/singleton pattern.

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
| LWJGL | 3.4.0 | OpenGL, GLFW, STB bindings |
| JOML | 1.10.8 | Vector/matrix math |
| Ashley | 1.7.3 | Entity Component System |
| SLF4J Simple | 2.0.17 | Logging |

LWJGL natives are resolved automatically at build time based on OS/arch.

## Platform Notes

- **macOS:** `-XstartOnFirstThread` is added automatically by the sandbox build config.
- **High-DPI / Retina:** Handled via `ScreenPixelRatioHandler` and the `-Dorg.lwjgl.opengl.Display.enableHighDPI=true` JVM flag.
- LWJGL debug loader is enabled by default (`-Dorg.lwjgl.util.Debug=true`).
