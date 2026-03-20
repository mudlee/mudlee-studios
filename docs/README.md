# Mudlee Studios Engine Documentation

A Java-based 2D/3D game engine built on LWJGL with a Vulkan rendering backend. The engine follows MonoGame-style architecture adapted to Java conventions.

## Table of Contents

### Fundamentals
1. [Getting Started](01-getting-started.md) — Build, run, project structure
2. [Architecture Overview](02-architecture-overview.md) — High-level design, module map, data flow diagrams
3. [Application Lifecycle](03-application-lifecycle.md) — Game loop, Game class, GameModule system

### Windowing & Input
4. [Window Management](04-window-management.md) — GLFW window, events, pixel ratio
5. [Input System](05-input-system.md) — Keyboard, mouse, gamepad, action mapping

### Rendering
6. [Rendering Architecture](06-rendering-architecture.md) — Hardware abstraction layer, Renderer facade, GraphicsContext
7. [Vulkan Backend Deep Dive](07-vulkan-backend.md) — Instance, device, swapchain, command pools
8. [Vulkan Pipeline & Shaders](08-vulkan-pipeline-and-shaders.md) — Graphics pipeline creation, push constants, shader system
9. [Vulkan Memory & Buffers](09-vulkan-memory-and-buffers.md) — VMA allocator, vertex/index buffers, staging uploads
10. [Vulkan Synchronization](10-vulkan-synchronization.md) — Frames in flight, fences, semaphores, deferred release
11. [Textures & Sprites](11-textures-and-sprites.md) — Texture loading, sprite sheets, SpriteBatch2D
12. [Cameras](12-cameras.md) — 2D orthographic, 3D perspective, free camera controller
13. [3D Mesh System](13-mesh-system.md) — Mesh3D, CubeMesh, render coordinators
14. [Render Targets](14-render-targets.md) — Off-screen rendering, render passes

### Game Systems
15. [Entity Component System](15-ecs.md) — World, entities, components, systems, queries
16. [Screen Management](16-screen-management.md) — Screen interface, ScreenManager stack
17. [Content Management](17-content-management.md) — Asset loading, caching, ContentManager
18. [Animation System](18-animation-system.md) — 2D sprite animation, playback modes
19. [UI System](19-ui-system.md) — UICanvas, UIManager, debug overlay

### Reference
20. [Shader Reference](20-shader-reference.md) — All GLSL shaders explained line by line
21. [Sandbox Examples](21-sandbox-examples.md) — How the sandbox application uses the engine
22. [Complete File Reference](22-file-reference.md) — Every file in the project with description
