# 8. Vulkan Pipeline & Shaders

## Overview

In Vulkan, a **graphics pipeline** is an immutable object that defines the entire rendering state: shaders, vertex format, blending, depth testing, rasterization, and more. Unlike OpenGL where you set state incrementally, Vulkan requires you to create pipeline objects up front.

This engine uses **deferred pipeline creation** — pipelines are created lazily on the first draw call that needs them, then cached for reuse.

## Pipeline Architecture

```mermaid
graph TB
    subgraph "VulkanShader"
        VM[Vert Module]
        FM[Frag Module]
        PL[Pipeline Layout]
        DSL[Descriptor Set Layout]
        PC[Push Constant Ranges]
        CACHE["Pipeline Cache<br/>(key → VkPipeline)"]
    end

    subgraph "Pipeline Key"
        VL[Vertex Layouts]
        RP[Render Pass]
    end

    subgraph "ShaderConfig"
        DT[Depth Test]
        DW[Depth Write]
        BL[Blending]
        CM[Cull Mode]
        MM[Model Matrix]
    end

    VL --> CACHE
    RP --> CACHE

    VM --> PL
    FM --> PL
    DSL --> PL
    PC --> PL
    PL --> CACHE
    ShaderConfig --> CACHE
```

## VulkanShader

`VulkanShader` (`core/src/main/java/hu/mudlee/core/render/vulkan/VulkanShader.java`) manages shader modules, pipeline layout, and cached pipelines.

### Shader Loading

1. GLSL source file paths are provided (e.g., `vulkan/3d/vert.glsl`)
2. The engine looks for a pre-compiled `.spv` file at the same path with `.spv` extension
3. If no `.spv` exists, it compiles GLSL → SPIR-V at runtime using **shaderc**
4. SPIR-V bytecode is loaded into `VkShaderModule` objects

### Pipeline Layout

The pipeline layout defines how data is passed to shaders:

```mermaid
graph LR
    subgraph "Push Constants (per-draw)"
        P["Projection Matrix<br/>64 bytes (mat4)"]
        V["View Matrix<br/>64 bytes (mat4)"]
        M["Model Matrix<br/>64 bytes (mat4)<br/>(optional)"]
    end

    subgraph "Descriptor Sets"
        DS0["Set 0, Binding 0<br/>Combined Image Sampler<br/>(texture)"]
    end

    PL[Pipeline Layout]
    P --> PL
    V --> PL
    M --> PL
    DS0 --> PL
```

### Push Constants

Push constants are the fastest way to pass small amounts of data to shaders. This engine passes **matrices** as push constants:

| Range | Size | Stage | Content |
|-------|------|-------|---------|
| Offset 0 | 128 bytes | Vertex | Projection (64B) + View (64B) |
| Offset 128 | 64 bytes | Vertex | Model matrix (optional, if `usesModelMatrix=true`) |

Total: 128 or 192 bytes per draw call (well within Vulkan's minimum guarantee of 128 bytes for vertex stage).

### Pipeline Caching

Pipelines are cached by a composite key:

```java
record PipelineKey(
    List<VertexBufferLayout> vertexLayouts,
    long renderPassHandle
)
```

This means the same shader can be used with different vertex formats or render targets, each getting its own cached pipeline.

### Pipeline Creation Details

When a new pipeline is needed, `VulkanShader` creates it with:

| Pipeline Stage | Configuration |
|----------------|---------------|
| **Vertex Input** | Derived from `VertexBufferLayout` (bindings + attributes) |
| **Input Assembly** | Triangle list topology |
| **Viewport/Scissor** | Dynamic state (set per draw) |
| **Rasterizer** | Fill mode, configurable cull mode, CCW front face |
| **Multisampling** | Disabled (1 sample) |
| **Depth/Stencil** | Configurable via `ShaderConfig` |
| **Color Blending** | Configurable (alpha blend for 2D, opaque for 3D) |
| **Dynamic State** | Viewport + Scissor |

## ShaderConfig

`ShaderConfig` (`core/src/main/java/hu/mudlee/core/render/types/ShaderConfig.java`) configures pipeline behavior:

```java
public record ShaderConfig(
    boolean depthTestEnabled,
    boolean depthWriteEnabled,
    boolean blendingEnabled,
    boolean usesModelMatrix,
    ShaderCullMode cullMode
)
```

### Preset Configurations

| Preset | Depth Test | Depth Write | Blending | Model Matrix | Cull Mode |
|--------|-----------|-------------|----------|-------------|-----------|
| `default2D()` | OFF | OFF | ON | OFF | NONE |
| `default3D()` | ON | ON | OFF | ON | BACK |

The engine can also auto-detect the preset from the shader path — if the path contains `/3d/`, it uses `default3D()`.

## Shader Uniform Flow

```mermaid
sequenceDiagram
    participant Game as Game Code
    participant S as VulkanShader
    participant VC as VulkanContext
    participant GPU as GPU

    Game->>S: setUniform("projection", mat4)
    Note over S: Stores matrix in projectionData[]

    Game->>S: setUniform("view", mat4)
    Note over S: Stores matrix in viewData[]

    Game->>S: setUniform("model", mat4)
    Note over S: Stores matrix in modelData[]

    Game->>VC: renderRaw(vao, shader)
    VC->>S: getPipeline(layouts, renderPass)
    Note over S: Returns cached or creates new

    VC->>GPU: vkCmdBindPipeline
    VC->>GPU: vkCmdPushConstants(projection + view)
    VC->>GPU: vkCmdPushConstants(model)
    VC->>GPU: vkCmdBindVertexBuffers
    VC->>GPU: vkCmdDrawIndexed
```

## Vertex Input Mapping

The vertex layout is defined in Java and automatically mapped to Vulkan's `VkVertexInputBindingDescription` and `VkVertexInputAttributeDescription`:

### 2D Sprite Vertex (36 bytes)

| Location | Attribute | Type | Offset |
|----------|-----------|------|--------|
| 0 | Position | vec3 (12B) | 0 |
| 1 | Color | vec4 (16B) | 12 |
| 2 | TexCoords | vec2 (8B) | 28 |

### 3D Colored Vertex (28 bytes)

| Location | Attribute | Type | Offset |
|----------|-----------|------|--------|
| 0 | Position | vec3 (12B) | 0 |
| 1 | Color | vec4 (16B) | 12 |

## Descriptor Sets

Textures are bound via Vulkan descriptor sets:

```mermaid
graph TB
    subgraph "Descriptor Infrastructure"
        DSL["Descriptor Set Layout<br/>(set=0, binding=0, sampler)"]
        DP["Descriptor Pool<br/>(pool-of-pools)"]
    end

    subgraph "Per-Texture"
        DS1["Descriptor Set A<br/>→ Texture A"]
        DS2["Descriptor Set B<br/>→ Texture B"]
    end

    DSL --> DP
    DP --> DS1
    DP --> DS2
```

Each `VulkanTexture2D` gets a pre-allocated descriptor set that points to its image/sampler. During rendering, the descriptor set is bound with `vkCmdBindDescriptorSets`.

### Pool-of-Pools Pattern

Vulkan descriptor pools have a fixed capacity. When one fills up, the engine allocates a new pool:

```
DescriptorPool 1 [full: 64/64 sets]
DescriptorPool 2 [partial: 30/64 sets]
DescriptorPool 3 [empty: 0/64 sets]
```

Freed descriptor sets are returned to their pool for reuse.

## ShaderProps Constants

`ShaderProps` (`core/src/main/java/hu/mudlee/core/render/types/ShaderProps.java`) defines uniform name constants:

| Constant | Value | Usage |
|----------|-------|-------|
| `UNIFORM_PROJECTION_MATRIX` | `"projection"` | Camera projection |
| `UNIFORM_VIEW_MATRIX` | `"view"` | Camera view transform |
| `UNIFORM_MODEL_MATRIX` | `"model"` | Object world transform |
| `UNIFORM_COLOR` | `"color"` | Flat color |
| `UNIFORM_TEXTURE` | `"texture"` | Texture sampler |
