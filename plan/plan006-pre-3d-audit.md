# Plan 006 — Pre-3D Codebase Audit

This document catalogues every concrete issue found in the codebase that will either
block 3D rendering or represents a wrong/leaky abstraction worth fixing before adding
complexity. Nothing here is speculative — every finding is grounded in a specific file
and line.

---

## 1. Dead `scene` package (duplicates `Screen`/`ScreenManager`)

**Files:** `core/scene/Scene.java`, `core/scene/SceneManager.java`
**Symptom:** `Game.loop()` calls `SceneManager.onUpdate(gameTime)` every frame, but
`SceneManager.setScreen()` is never called anywhere in the codebase. The `scene`
package is entirely superseded by `core/Screen.java` + `core/ScreenManager.java`
(the `GameService`-based stack).

**Problem:** Two parallel, incompatible scene systems create confusion about which one
to use. The `Scene` interface has `start/update/resize/dispose`; `Screen` has
`show/update/draw/resize/hide/dispose`. They are not the same contract.

**Fix:** Delete `core/scene/Scene.java` and `core/scene/SceneManager.java`.
Remove the `SceneManager.onUpdate()` / `SceneManager.onWindowResized()` /
`SceneManager.onDispose()` calls from `Game.java` (they are no-ops anyway since no
screen is ever set on that manager).

---

## 2. `Component.draw()` and `GameObject.draw()` hardcode `SpriteBatch2D`

**Files:** `gameobject/Component.java:34`, `gameobject/GameObject.java:87`

```java
public void draw(GameTime gameTime, SpriteBatch2D batch) {}
```

**Problem:** Every component's draw method receives a `SpriteBatch2D`. A 3D mesh
renderer component cannot use `SpriteBatch2D` — it needs a completely different draw
context (material, camera, light list). Keeping this signature means a `GameScene3D`
would have to invent a different lifecycle or break the abstraction.

**Fix:** Introduce a `RenderContext` interface (or a simple scene-level render bag)
passed to `draw()`. `SpriteBatch2D` is an implementation detail of 2D scenes, not
a universal draw primitive. The `RenderContext` can carry whatever the active scene
type needs.

---

## 3. `Transform2D` is hardcoded on `GameObject` (UPCOMING)

**Files:** `gameobject/GameObject.java:26`, `gameobject/Transform2D.java`

```java
public final Transform2D transform = new Transform2D();
```

`Transform2D` uses `Vector2f position`, `float rotation`, `Vector2f scale`. Every
`GameObject` in the engine always has a 2D-only transform, even if the scene is 3D.

**Fix:** Replace the field type with a `Transform` interface.
The `SpriteRenderer2D` and `Animator2D` components read `gameObject.transform`
directly — those reads would need to cast.

---

## 4. `GameScene2D` hardcodes both `SpriteBatch2D` and `Camera2D`

**File:** `gameobject/GameScene2D.java:38-39`

```java
protected Camera2D camera;
protected SpriteBatch2D spriteBatch;
```

`show()` always creates an `OrthographicCamera2D` and a `SpriteBatch2D`. A 3D
scene would need a `PerspectiveCamera` and a mesh renderer, not a sprite batch.
The lifecycle (`draw()` calls `spriteBatch.begin/end`) is baked in.

**Fix:** Extract a common `GameScene` base that owns only game-object lifecycle
(`addGameObject`, `removeGameObject`, the `gameObjects` list, `update()`). The
rendering setup belongs in `GameScene2D` and a future `GameScene3D`, not the
shared base.

---

## 5. `BufferBitTypes` leaks raw OpenGL constants into the public API

**File:** `render/types/BufferBitTypes.java`

```java
public static final int COLOR = GL_COLOR_BUFFER_BIT; // raw OpenGL value
```

`GraphicsDevice.clear(Color)` calls `Renderer.setClearFlags(BufferBitTypes.COLOR)`,
passing a raw OpenGL integer (`0x4000`) through the HAL boundary. Vulkan does not use
these bit masks. This also means there is no way to clear the depth buffer from game
code — `GraphicsDevice.clear()` only accepts a colour.

**Fix:** Replace `BufferBitTypes` with a proper `ClearFlag` enum (`COLOR`, `DEPTH`,
`STENCIL`). Translate to backend constants inside `OpenGLGraphicsContext.clear()`.
Change `GraphicsDevice.clear(Color)` to `GraphicsDevice.clear(Color, ClearFlag...)`
so game code can request depth clearing for 3D scenes.

---

## 6. `PolygonMode`, `RenderMode`, and `ShaderTypes` hold raw OpenGL constant values

**Files:** `render/types/PolygonMode.java`, `render/types/RenderMode.java`,
`render/types/ShaderTypes.java`

```java
TRIANGLES(GL_TRIANGLES)  // stores OpenGL integer directly in the enum
LINE(GL_LINE)
FLOAT = GL_FLOAT
```

These enum fields store OpenGL integers. Vulkan uses `VkPrimitiveTopology`,
`VkPolygonMode`, and typed format enums — the values are different. Currently the
Vulkan backend has to ignore or work around the `glRef` field.

**Fix:** Remove `glRef` (and `FLOAT`/`UNSIGNED_BYTE` raw values) from the
HAL-facing types. Backend implementations translate the enum to their own constant
internally. `ShaderTypes` can become an enum with a name only.

---

## 7. `Shader` API exposes OpenGL PPO concepts

**File:** `render/Shader.java`

```java
int getVertexProgramId();
int getFragmentProgramId();
void setUniform(int programId, String name, Matrix4f value);
```

`getVertexProgramId()` / `getFragmentProgramId()` return OpenGL Program Pipeline Object
IDs. Vulkan has no concept of a "program ID". `setUniform(int programId, ...)` passes
the program ID as a parameter, which is also OpenGL-specific. In Vulkan, uniforms are
push constants or UBOs — setting them does not involve a program ID.

**Fix:** Remove `getVertexProgramId()` / `getFragmentProgramId()` from `Shader`.
Replace `setUniform(int programId, String name, T value)` with
`setUniform(String name, T value)`. The OpenGL implementation can look up the correct
program ID internally (it already owns both `vertexId` and `fragmentId`).

---

## 8. `SpriteBatch2D` uses non-indexed quads (6 vertices per sprite)

**File:** `render/SpriteBatch2D.java:35-36`

```java
private static final int VERTICES_PER_SPRITE = 6; // two non-indexed triangles
```

Each sprite uploads 6 vertices instead of 4. With an index buffer (4 vertices + 6
indices per quad), the vertex data shrinks by 33%. At 1000 sprites that is 18,000 vs
24,000 floats per flush. This is important for a batching system.

**Fix:** Change `SpriteBatch2D` to use 4 vertices per sprite with a shared static index
buffer pre-filled with the `[0,1,2, 0,2,3]` pattern. This is a straightforward change
that improves throughput.

---

## 9. `OpenGLTexture2D.bind()` always activates texture unit 0

**File:** `render/opengl/OpenGLTexture2D.java:83`

```java
glActiveTexture(GL_TEXTURE0); // TODO: we should not use it here...
```

There is already a TODO here. For 3D rendering, you need multiple texture units
simultaneously (albedo, normal map, specular, shadow map). Hardcoding unit 0 makes
multi-texturing impossible without workarounds.

**Fix:** Add a `bind(int unit)` overload to the `Texture2D` abstract class and have
callers specify the unit. Keep `bind()` as `bind(0)` for backward compatibility.

---

## 10. `Renderer.clear()` resets the draw call counter as a side effect

**File:** `render/Renderer.java:103-106`

```java
public static void clear() {
    drawCallCount = 0; // side effect: resets counter
    get().context.clear();
}
```

`GameScene2D.draw()` calls `graphicsDevice.clear()` which calls `Renderer.clear()`.
If the counter is reset here, any draw calls that happen *before* the scene clears
(e.g. a loading screen rendering before the game scene) would disappear from the
count. Conceptually "reset frame counters" is a different operation from "clear the
framebuffer".

**Fix:** Reset `drawCallCount` at the start of the frame in `Game.loop()`, not
inside `clear()`.

---

## 11. `GraphicsContext.renderRaw()` range overload is a silent no-op default

**File:** `render/GraphicsContext.java:22-28`

```java
default void renderRaw(..., int elementOffset, int elementCount) {}
```

The range-draw overload has an empty default body. If a Vulkan backend forgets to
implement it, it silently renders nothing rather than failing fast. The main
`renderRaw()` is correctly declared abstract (no default body).

**Fix:** Throw `UnsupportedOperationException` in the default body, or make it
abstract. Silence is worse than an exception here.

---

## 12. `VertexBuffer.update()` throws `UnsupportedOperationException` by default

**File:** `render/VertexBuffer.java:22-27`

```java
public void update(float[] data, int floatCount) {
    throw new UnsupportedOperationException(...);
}
```

Static and dynamic buffers are the same class with runtime-checked behaviour. A static
buffer fails at runtime when you try to update it, not at compile time.

**Fix:** Separate `StaticVertexBuffer` and `DynamicVertexBuffer` implementations, or
mark the method `abstract` and require all subclasses to implement it (static ones
throw, dynamic ones update). The same issue exists in `ElementBuffer`.

---

## 13. `OpenGLGraphicsContext.renderRaw()` derives vertex count by dividing by 3

**File:** `render/opengl/OpenGLGraphicsContext.java:106`

```java
glDrawArrays(renderMode.glRef, 0, buffer.getLength() / 3);
// NOTE: we suppose that vertex coordinates always passed as vec3
```

This hardcodes the assumption that all vertices are vec3 positions. A vertex buffer
storing `(vec3 pos, vec3 normal, vec2 uv)` would compute the wrong count. This will
break with any 3D vertex layout.

**Fix:** The vertex count should be derived from `buffer.getLength() / (stride / Float.BYTES)`
where stride comes from the buffer's layout, or simply stored as a vertex count at
buffer creation time.

---

## 14. `ContentManager` is created but never used

**File:** `core/Game.java:50-52`, `core/content/ContentManager.java`

```java
if (content == null) {
    content = new ContentManager("");
}
```

`ContentManager` is instantiated in every `Game.run()` call but no existing game code
calls `content.load(...)`. It is dead infrastructure occupying a `protected` field on
the base class.

**Fix:** Either wire it up as the intended asset-loading API (replacing the scattered
`Texture2D.create(path)` calls throughout the codebase), or remove it until it is
actually needed. Leaving it as dead code on the base class is misleading.

---

## 15. `Game.components` is a raw public mutable list

**File:** `core/Game.java:24`

```java
public final List<GameService> components = new ArrayList<>();
```

Game code adds services in any order and the order matters (UIService must come after
ScreenManager). There is no dependency declaration, no documentation of order
requirements, and no enforcement. This has already caused a rendering bug this session.

**Fix:** Replace the public list with `addComponent(GameService)` and
`removeComponent(GameService)` methods on `Game`. This is a small API change that
opens the door for ordering enforcement or dependency tracking later.

---

## Summary table

| # | File(s) | Severity for 3D | Kind |
|---|---------|-----------------|------|
| 1 | `scene/Scene`, `scene/SceneManager` | Low | Dead code |
| 2 | `Component.draw`, `GameObject.draw` | **Critical** | Wrong abstraction |
| 3 | `GameObject.transform` as `Transform2D` | **Critical** | Wrong abstraction |
| 4 | `GameScene2D` ownership of SpriteBatch/Camera | **High** | Wrong abstraction |
| 5 | `BufferBitTypes` + `GraphicsDevice.clear()` | **High** | HAL leak |
| 6 | `PolygonMode`/`RenderMode`/`ShaderTypes` GL values | **High** | HAL leak |
| 7 | `Shader` PPO API | **High** | HAL leak |
| 8 | `SpriteBatch2D` non-indexed quads | Medium | Performance |
| 9 | `Texture2D.bind()` hardcoded unit 0 | **High** | HAL limitation |
| 10 | `Renderer.clear()` side effect | Low | Logic bug |
| 11 | `GraphicsContext.renderRaw()` silent no-op | Medium | Bug risk |
| 12 | `VertexBuffer.update()` runtime exception | Low | Design |
| 13 | `renderRaw()` vertex count `/3` hack | **High** | Correctness |
| 14 | `ContentManager` unused | Low | Dead code |
| 15 | `Game.components` raw public list | Low | Design |
