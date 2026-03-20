# 17. Content Management

## Overview

The content system provides centralized asset loading with caching, similar to MonoGame's `ContentManager`.

## Architecture

```mermaid
graph TB
    subgraph "ContentManager"
        CACHE["Cache<br/>Map&lt;key, asset&gt;"]
        LOADERS["Loaders<br/>Map&lt;type, ContentLoader&gt;"]
    end

    subgraph "Built-in Loaders"
        TL[Texture2D Loader]
        FL[BitmapFont Loader]
        AL[TextureAtlas Loader]
    end

    subgraph "Assets"
        T[Texture2D]
        F[BitmapFont]
        A[TextureAtlas]
    end

    LOADERS --> TL
    LOADERS --> FL
    LOADERS --> AL

    TL --> T
    FL --> F
    AL --> A

    T --> CACHE
    F --> CACHE
    A --> CACHE
```

## ContentManager

`ContentManager` (`core/src/main/java/hu/mudlee/core/content/ContentManager.java`):

```java
// Available via this.content in Game subclass

// Load a texture (cached — second call returns same instance)
var texture = content.load(Texture2D.class, "textures/mario.png");

// Load a font
var font = content.load(BitmapFont.class, "fonts/Inter.ttf");

// Unload all cached assets (calls dispose on each)
content.unload();
```

### Key Behaviors

| Behavior | Description |
|----------|-------------|
| **Caching** | Assets loaded once by name; subsequent calls return the cached instance |
| **Type dispatch** | The `Class<T>` parameter selects the appropriate `ContentLoader` |
| **Bulk unload** | `unload()` disposes all cached assets (called during `unloadContent()`) |

## ContentLoader

`ContentLoader<T>` (`core/src/main/java/hu/mudlee/core/content/ContentLoader.java`) is the interface for asset loaders:

```java
public interface ContentLoader<T> {
    T load(ContentManager manager, String assetName);
}
```

### Registering Custom Loaders

```java
content.registerLoader(MyAssetType.class, (manager, name) -> {
    // Load and return the asset
    return new MyAssetType(name);
});
```

## ResourceLoader

`ResourceLoader` (`core/src/main/java/hu/mudlee/core/io/ResourceLoader.java`) is a utility for loading raw files from the classpath:

```java
String shaderSource = ResourceLoader.load("shaders/vulkan/3d/vert.glsl");
```

This is used internally by shader loading and can be used for any text resource.

## Asset Lifecycle

```mermaid
flowchart TD
    A["content.load(Texture2D, 'mario.png')"] --> B{In cache?}
    B -->|Yes| C[Return cached]
    B -->|No| D[Find loader for Texture2D]
    D --> E[Loader reads file from classpath]
    E --> F[Create GPU resource]
    F --> G[Store in cache]
    G --> C

    H["content.unload()"] --> I[Iterate all cached assets]
    I --> J["asset.dispose() for each"]
    J --> K[Clear cache]
```

## Built-in Loaders

| Type | Source | Creates |
|------|--------|---------|
| `Texture2D` | PNG/JPG via STB Image | VulkanTexture2D with GPU image + descriptor set |
| `BitmapFont` | TTF font file | Bitmap font with glyph atlas |
| `TextureAtlas` | Texture + region definitions | Atlas with named TextureRegion entries |
