# 1. Getting Started

## Prerequisites

- **Java 17+** (the project uses Java Platform Module System)
- **Gradle** (wrapper included, no separate install needed)
- **Vulkan-capable GPU** with up-to-date drivers
- **macOS note**: MoltenVK is used as the Vulkan implementation; LWJGL bundles it automatically

## Building & Running

```bash
# Run the sandbox application (builds automatically)
./gradlew run

# Build all modules without running
./gradlew build

# Format code (Palantir Java Format, 120 char lines)
./gradlew spotlessApply

# Check formatting without modifying
./gradlew spotlessCheck

# Create a self-contained runtime image via jlink
./gradlew jlink

# Run the packaged application
./sandbox/build/image/bin/sandbox-app
```

## Project Structure

```
mudlee-studios/
├── core/                          # Engine library (java-library)
│   ├── build.gradle.kts
│   └── src/main/java/
│       ├── module-info.java       # JPMS module descriptor
│       └── hu/mudlee/core/        # All engine source code
│           ├── *.java             # Core types (Game, Window, etc.)
│           ├── content/           # Asset loading
│           ├── ecs/               # Entity Component System
│           │   ├── component/     # Built-in components
│           │   └── system/        # Built-in systems
│           ├── input/             # Input handling
│           ├── io/                # Resource loading utilities
│           ├── render/            # Rendering abstraction
│           │   ├── animation/     # 2D animation
│           │   ├── camera/        # 2D and 3D cameras
│           │   ├── font/          # Bitmap font rendering
│           │   ├── mesh/          # 3D mesh system
│           │   ├── texture/       # Texture loading and atlases
│           │   ├── types/         # Shader types and config
│           │   └── vulkan/        # Vulkan backend implementation
│           ├── settings/          # Window/engine preferences
│           ├── ui/                # UI overlay system
│           └── window/            # GLFW window management
│
├── sandbox/                       # Example application
│   ├── build.gradle.kts
│   └── src/main/java/
│       ├── module-info.java
│       └── hu/mudlee/sandbox/     # Demo scenes and systems
│
├── resources/                     # Shared assets (included in core)
│   ├── shaders/vulkan/            # GLSL + compiled SPIR-V shaders
│   │   ├── 2d/                    # 2D sprite pipeline
│   │   └── 3d/                    # 3D colored geometry pipeline
│   ├── textures/                  # Texture assets
│   ├── fonts/                     # Font files
│   └── simplelogger.properties    # SLF4J configuration
│
├── build.gradle.kts               # Root build file
├── settings.gradle.kts            # Gradle settings (submodules)
└── gradle.properties              # Shared Gradle properties
```

## Module System

Both `core` and `sandbox` use Java Platform Module System (JPMS):

- **`hu.mudlee.core`** — The engine module. Exports all public packages. Requires LWJGL, JOML, SLF4J.
- **`hu.mudlee.sandbox`** — The demo application module. Requires only `hu.mudlee.core`.

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| LWJGL   | 3.4.0   | GLFW, Vulkan, STB, Shaderc, VMA bindings |
| JOML    | 1.10.8  | Vector and matrix math |
| SLF4J Simple | 2.0.17 | Logging |

## JVM Flags

The sandbox automatically sets these JVM flags:

| Flag | Purpose |
|------|---------|
| `-XstartOnFirstThread` | Required by GLFW on macOS |
| `-Dorg.lwjgl.system.allocator=system` | Use system malloc instead of LWJGL's allocator |
| `-Dorg.lwjgl.util.Debug=true` | Enable LWJGL debug output |
| `-Dorg.lwjgl.util.DebugLoader=true` | Debug native library loading |
