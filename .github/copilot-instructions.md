# GitHub Copilot Instructions

## Most Important Rules

- **No co-author attribution** — never add `Co-Authored-By:` lines to commit messages.
- **Always ask before committing** — never create a git commit without explicit user approval.
- **Always ask before pushing** — never push to the repository without explicit user approval.
- **Always run spotlessApply before committing** — ensure code is formatted correctly before any commit.
- **Always verify the app runs after bigger changes** — run `./gradlew run`, wait 8 seconds, confirm the window opens without errors in the output, then stop it.

## Agent Role & Expertise

- Lead Java Architect: Senior Java developer, Clean Code principles, SOLID design, Java 17/21+.
- Graphics Engineering Specialist: Expert in OpenGL 4.5+ and Vulkan 1.3 (memory barriers, pipeline states, command buffers, descriptor sets).

## Project Context

Java-based game engine using LWJGL. Goal: a 2D/3D rendering engine similar to LibGDX and MonoGame.

**Key references:**
- MonoGame API: https://docs.monogame.net/api/index.html
- MonoGame Extended: https://www.monogameextended.net/docs/about/introduction/
- MonoGame samples: https://github.com/MonoGame/MonoGame.Samples
- Vulkan tutorial: https://vulkan-tutorial.com/

**Key guidelines:**
- Mirror MonoGame's structure and API in Java style.
- Hardware abstraction layer (HAL) so OpenGL/Vulkan can be toggled.
- Strictly typed Java; avoid GC pressure in the render loop (object pooling, direct buffers).

## Build & Run Commands

```bash
./gradlew run          # Run the sandbox application — use this to verify changes work
./gradlew build        # Build all modules
./gradlew spotlessApply  # Format code in-place (palantir-java-format, 120 char line length)
./gradlew spotlessCheck  # Verify formatting without modifying
```

There are no automated tests. The sandbox (`./gradlew run`) is the manual testing environment.

## Key Files to Load as Context

| File                                                              | Why                                                      |
|-------------------------------------------------------------------|----------------------------------------------------------|
| `CLAUDE.md` / `AGENTS.md`                                         | Full project instructions and conventions                |
| `core/src/main/java/module-info.java`                             | Module exports — update when adding new packages         |
| `core/src/main/java/hu/mudlee/core/Game.java`                     | Main game loop and lifecycle                             |
| `core/src/main/java/hu/mudlee/core/GameService.java`              | Base class for game-level services (ScreenManager, etc.) |
| `core/src/main/java/hu/mudlee/core/ScreenManager.java`            | Screen/scene stack management                            |
| `core/src/main/java/hu/mudlee/core/gameobject/GameObject.java`    | Entity base class                                        |
| `core/src/main/java/hu/mudlee/core/gameobject/Component.java`     | Entity-level component base class                        |
| `core/src/main/java/hu/mudlee/core/gameobject/GameScene.java`     | Scene that manages GameObjects                           |
| `core/src/main/java/hu/mudlee/core/render/SpriteBatch.java`       | 2D batched rendering                                     |
| `sandbox/src/main/java/hu/mudlee/sandbox/SandboxApplication.java` | Entry point                                              |

## Project Structure

- **`core/`** — Engine library. Rendering, ECS, scenes, input, window, cameras.
- **`sandbox/`** — Manual test app. Entry: `hu.mudlee.sandbox.SandboxApplication`.
- **`resources/`** — Shared assets: GLSL/SPIR-V shaders, textures, logging config.

## Architecture Overview

- **Game loop**: `Game` → `Window` (GLFW) + `Renderer` + `ScreenManager` + `GameService` list.
- **Screens**: `Screen` interface managed by `ScreenManager` (stack-based).
- **GameObjects**: `GameScene` holds `GameObject` instances; each has `Component`s (`SpriteRenderer`, `Animator`, etc.) and a `Transform`.
- **Rendering**: `Renderer` facade over `GraphicsContext` (OpenGL or Vulkan backend).
- **ECS**: Ashley framework — legacy path, separate from the new GameObject system.
- **Cameras**: `Camera2D` / `OrthoCamera` supply projection+view matrices as shader uniforms.

## Code Style Rules

### Formatting
palantir-java-format, 120 character line length, enforced via Spotless. Always run `./gradlew spotlessApply` before finishing.

### Comments
- No section-divider banners (`// --- Section ---`).
- Only comment methods that are not self-explanatory.

### Mandatory Curly Braces
Every `if`, `else`, `for`, `while`, `do` body must use curly braces even for single-line bodies.

```java
// Wrong
if (condition) return;

// Correct
if (condition) { return; }
```

### Class Member Ordering (Google Java Style §3.4.2)
1. Static constants
2. Static fields
3. Instance fields
4. Constructors
5. Public/protected methods
6. Private methods
7. Inner classes/interfaces/enums

Static before instance, public before private within each group. No banner comments.

### Local Variable Type Inference
Use `var` for all locals where the type is obvious from the right-hand side. Do **not** use for fields, parameters, return types, or ambiguous literals.

```java
var batch = new SpriteBatch();   // correct
var x = 0;                       // wrong — ambiguous (int? long? float?)
```

## Dependencies

| Library      | Version | Purpose                            |
|--------------|---------|------------------------------------|
| LWJGL        | 3.4.0   | OpenGL, GLFW, STB, Vulkan bindings |
| JOML         | 1.10.8  | Vector/matrix math                 |
| Ashley       | 1.7.3   | Entity Component System            |
| SLF4J Simple | 2.0.17  | Logging                            |

## Platform Notes

- macOS: `-XstartOnFirstThread` added automatically.
- High-DPI handled via `ScreenPixelRatioHandler`.
- LWJGL debug loader enabled by default.
