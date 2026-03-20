# 2. Architecture Overview

## Design Philosophy

The engine follows **MonoGame's architecture** adapted to Java:

- **`Game`** is the central class (like MonoGame's `Game`)
- **`GraphicsDevice`** is the public GPU facade
- **`GraphicsDeviceManager`** configures the graphics backend
- **`Screen`** / **`ScreenManager`** manage game states (like MonoGame's `GameScreen`)
- **`ContentManager`** handles asset loading with caching
- **`SpriteBatch2D`** batches 2D draw calls (like MonoGame's `SpriteBatch`)

## High-Level Module Map

```mermaid
graph TB
    subgraph "Sandbox (Application)"
        SA[SandboxApplication]
        CS[CubeScene]
        PS[PlayerScene]
    end

    subgraph "Core Engine"
        subgraph "Framework Layer"
            G[Game]
            GDM[GraphicsDeviceManager]
            GD[GraphicsDevice]
            SM[ScreenManager]
            CM[ContentManager]
        end

        subgraph "Rendering Layer"
            R[Renderer]
            GC[GraphicsContext]
            SB[SpriteBatch2D]
            MRC[MeshRenderCoordinator]
            CAM[Cameras]
        end

        subgraph "Vulkan Backend"
            VC[VulkanContext]
            VI[VulkanInstance]
            VD[VulkanDevice]
            VSC[VulkanSwapChain]
            VCP[VulkanCommandPool]
            VSO[VulkanSyncObjects]
            VA[VulkanAllocator]
        end

        subgraph "Game Systems"
            ECS[ECS World]
            IS[InputSystem]
            UI[UIManager]
        end
    end

    subgraph "External"
        GLFW[GLFW 3.4.0]
        VK[Vulkan API]
        STB[STB Image]
        JOML[JOML Math]
    end

    SA --> G
    CS --> GD
    CS --> MRC
    PS --> ECS
    PS --> SB

    G --> GDM
    G --> GD
    G --> SM
    G --> CM
    G --> R
    GD --> R

    R --> GC
    GC -.->|implements| VC
    SB --> R
    MRC --> R

    VC --> VI
    VC --> VD
    VC --> VSC
    VC --> VCP
    VC --> VSO
    VC --> VA

    VI --> VK
    VD --> VK
    G --> GLFW
    IS --> GLFW
    SB --> JOML
    CAM --> JOML
```

## Core Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Singleton** | `Window`, `Renderer`, `VulkanContext` | One GPU context, one window |
| **Abstract Factory** | `RenderBackendFactory` | Backend-agnostic object creation |
| **Strategy** | `GraphicsContext` interface | Swap rendering backends |
| **Facade** | `Renderer`, `GraphicsDevice` | Simple API over complex Vulkan calls |
| **Template Method** | `Game`, `SystemBase`, `Camera2D/3D` | Lifecycle hooks for subclasses |
| **Dirty Flag** | Cameras, transforms | Avoid recomputing unchanged matrices |
| **Object Pool** | Entity ID recycling, descriptor pool-of-pools | Reduce allocations |
| **Deferred Release** | `VulkanContext.deferRelease()` | GPU-safe resource destruction |

## Data Flow: One Frame

```mermaid
sequenceDiagram
    participant G as Game
    participant W as Window
    participant IS as InputSystem
    participant GD as GraphicsDevice
    participant R as Renderer
    participant VC as VulkanContext
    participant GPU as Vulkan GPU

    G->>IS: update()
    IS->>IS: poll gamepad, advance action states
    G->>W: pollEvents()
    W->>IS: keyboard/mouse callbacks

    G->>G: update(gameTime)
    Note over G: Game logic, ECS, screens

    G->>G: draw(gameTime)
    G->>GD: beginFrame(clearColor)
    GD->>R: setClearColor() + beginFrame()
    R->>VC: beginFrame()
    VC->>GPU: vkWaitForFences
    VC->>GPU: vkAcquireNextImageKHR
    VC->>GPU: vkBeginCommandBuffer

    Note over G: User draw calls happen here
    G->>R: beginRenderPass()
    R->>VC: beginRenderPass()
    VC->>GPU: vkCmdBeginRenderPass

    G->>R: renderRaw(vao, shader)
    R->>VC: renderRaw()
    VC->>GPU: vkCmdBindPipeline
    VC->>GPU: vkCmdPushConstants
    VC->>GPU: vkCmdBindVertexBuffers
    VC->>GPU: vkCmdDrawIndexed

    G->>R: endRenderPass()
    R->>VC: endRenderPass()
    VC->>GPU: vkCmdEndRenderPass

    G->>GD: present(frameTime)
    GD->>R: present()
    R->>VC: present()
    VC->>GPU: vkEndCommandBuffer
    VC->>GPU: vkQueueSubmit
    VC->>GPU: vkQueuePresentKHR
```

## Abstraction Layers

The rendering system has three abstraction levels:

```
┌─────────────────────────────────────────────┐
│          Game Code (Sandbox)                 │
│  Uses: GraphicsDevice, SpriteBatch2D,       │
│        Mesh3D, Shader, Texture2D            │
├─────────────────────────────────────────────┤
│          Engine Facade                       │
│  Renderer, RenderBackendFactory             │
│  Abstract: Shader, VertexArray, Texture2D   │
├─────────────────────────────────────────────┤
│          Backend Implementation              │
│  VulkanContext, VulkanShader,               │
│  VulkanVertexArray, VulkanTexture2D, etc.   │
├─────────────────────────────────────────────┤
│          LWJGL / Vulkan API                  │
└─────────────────────────────────────────────┘
```

Game code never touches Vulkan directly. It works with abstract types (`Shader`, `VertexArray`, `Texture2D`) created through `RenderBackendFactory`. This allows a future OpenGL or other backend to be swapped in by implementing `GraphicsContext` and `RenderBackendFactory`.
