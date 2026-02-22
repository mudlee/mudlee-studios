# Plan 001 — Engine API Transformation (MonoGame-style)

## Objective

Transform the engine's public API from its current low-level, scene-centric structure into a
clean, high-level game framework modelled after MonoGame. The internal rendering backends
(OpenGL, Vulkan) remain untouched; only the public-facing API that game code interacts with
changes.

## Reference

- MonoGame API: https://docs.monogame.net/api/index.html
- MonoGame Samples: https://github.com/MonoGame/MonoGame.Samples
- Current entry point to compare against: `sandbox/src/main/java/hu/mudlee/sandbox/scenes/GameScene.java`

---

## What the Current API Looks Like (Problem)

```java
// Game code today — leaks low-level GPU concepts into user code
public class GameScene implements Scene {
    private Shader shader;          // user manages raw shaders
    private VertexArray va;         // user manages VAOs
    private Camera2D camera;

    @Override
    public void start() {
        texture = Texture2D.create("/textures/mario.png");   // hard path, no asset manager
        shader = Shader.create("vulkan/2d/vert.glsl", "vulkan/2d/frag.glsl"); // user picks shaders
        shader.createUniform(...);  // user manually wires uniforms
        shader.setUniform(..., camera.getProjectionMatrix());

        var layout = new VertexBufferLayout(...);  // user defines vertex layout by hand
        va = VertexArray.create();
        va.addVBO(VertexBuffer.create(squareVertices, layout, BufferUsage.STATIC_DRAW));
        va.setEBO(ElementBuffer.create(squareIndices, BufferUsage.STATIC_DRAW));
        ECS.addEntity(new RawRenderableEntity("Square", va, shader, RenderMode.TRIANGLES, PolygonMode.FILL));
    }

    @Override
    public void update(float deltaTime) {
        if (KeyListener.isKeyPressed(GLFW_KEY_ESCAPE)) { Application.stop(); }
        shader.setUniform(...);  // user re-uploads matrices every frame
    }
}
```

## What the Target API Looks Like (Goal)

```java
// Game code after transformation — clean, no GPU concepts exposed
public class MyGame extends Game {

    private SpriteBatch spriteBatch;
    private Texture2D marioTexture;
    private Camera2D camera;

    public MyGame() {
        var gdm = new GraphicsDeviceManager(this);
        gdm.setPreferredBackBufferWidth(1280);
        gdm.setPreferredBackBufferHeight(720);
    }

    @Override
    protected void loadContent() {
        spriteBatch = new SpriteBatch(graphicsDevice);
        marioTexture = content.load(Texture2D.class, "textures/mario");
        camera = new Camera2D(graphicsDevice.getViewport());
    }

    @Override
    protected void update(GameTime gameTime) {
        if (Keyboard.getState().isKeyDown(Keys.ESCAPE)) { exit(); }
        camera.position.x -= gameTime.elapsedSeconds() * 50f;
        super.update(gameTime);
    }

    @Override
    protected void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        spriteBatch.begin(camera.getTransformMatrix());
        spriteBatch.draw(marioTexture, new Vector2(0.5f, 0.5f), Color.WHITE);
        spriteBatch.end();
        super.draw(gameTime);
    }

    @Override
    protected void unloadContent() {
        content.unload();
    }
}

// Entry point — no framework config, just run
public static void main(String[] args) {
    new MyGame().run();
}
```

---

## Transformation Steps

### Step 1 — Introduce `GameTime`

**What:** Replace the raw `float deltaTime` threaded through `update()` with a `GameTime`
value object, matching MonoGame's `GameTime`.

**New class:** `hu.mudlee.core.GameTime`

```java
public final class GameTime {
    public float elapsedSeconds();    // delta since last frame
    public float totalSeconds();      // total running time
    public boolean isRunningSlowly(); // true when frame took longer than target
}
```

**Touches:** `Application`, `Scene`, `SceneManager`, `GameScene`.

**Why first:** Everything downstream depends on this type; doing it early avoids re-touching
the same call sites multiple times.

---

### Step 2 — Replace `Application` + `Scene` + `SceneManager` with `Game`

**What:** Introduce an abstract `Game` class that game code extends instead of implementing
`Scene`. Remove the `Application` static singleton and `SceneManager` from the public API.

**New class:** `hu.mudlee.core.Game`

```java
public abstract class Game {
    protected GraphicsDevice graphicsDevice;
    protected ContentManager content;
    protected GameWindow window;

    // Lifecycle — override these
    protected void initialize() {}
    protected void loadContent() {}
    protected void update(GameTime gameTime) {}
    protected void draw(GameTime gameTime) {}
    protected void unloadContent() {}

    // Control
    public final void run();
    public final void exit();
}
```

`Application` becomes an internal implementation detail (`GameRunner`) that wires GLFW and
drives the loop — it is no longer part of the public API.

`Scene` / `SceneManager` are kept internally as an optional screen-stack feature (Step 9),
but are no longer the primary game structure.

**Why:** This is the biggest conceptual shift — users no longer interact with the framework
through a scene; they *are* the game.

---

### Step 3 — Introduce `GraphicsDeviceManager`

**What:** Replace `WindowPreferences` with a `GraphicsDeviceManager` that is constructed
inside the `Game` subclass constructor, matching MonoGame's pattern exactly.

**New class:** `hu.mudlee.core.render.GraphicsDeviceManager`

```java
public class GraphicsDeviceManager {
    public GraphicsDeviceManager(Game game);
    public GraphicsDeviceManager setPreferredBackBufferWidth(int w);
    public GraphicsDeviceManager setPreferredBackBufferHeight(int h);
    public GraphicsDeviceManager setVSync(boolean enabled);
    public GraphicsDeviceManager setFullscreen(boolean enabled);
    public GraphicsDeviceManager setPreferredBackend(RenderBackend backend);
}
```

`WindowPreferences` is retired; `GraphicsDeviceManager` replaces it end-to-end.

---

### Step 4 — Introduce `ContentManager`

**What:** Replace `Texture2D.create(hardcodedPath)` with a `ContentManager` that handles
asset discovery, caching, and bulk disposal.

**New class:** `hu.mudlee.core.content.ContentManager`

```java
public class ContentManager {
    public ContentManager(String rootDirectory);
    public <T> T load(Class<T> type, String assetName); // caches on first load
    public void unload();  // disposes all loaded assets
}
```

**Supported types initially:** `Texture2D`. Later: `SpriteFont`, `Effect`, `Model`.

Asset names are relative to `rootDirectory`, with no file extension (the manager resolves it).
The raw `Texture2D.create()` factory stays as an internal API for the manager to delegate to.

---

### Step 5 — Introduce `SpriteBatch`

**What:** Replace the manual `VertexArray` / `Shader` / `ECS` pipeline for 2D sprite
rendering with a high-level `SpriteBatch`. This is the biggest usability win.

**New class:** `hu.mudlee.core.render.SpriteBatch`

```java
public class SpriteBatch implements Disposable {
    public SpriteBatch(GraphicsDevice graphicsDevice);

    public void begin();
    public void begin(Matrix4f transformMatrix);

    // Core draw overloads
    public void draw(Texture2D texture, Vector2f position, Color color);
    public void draw(Texture2D texture, Vector2f position, Rectangle sourceRect, Color color);
    public void draw(Texture2D texture, Vector2f position, Color color,
                     float rotation, Vector2f origin, float scale, boolean flipX, boolean flipY);
    public void draw(Texture2D texture, Rectangle destinationRect, Color color);

    public void end(); // flushes all queued draw calls as a single batched draw
}
```

Internally, `SpriteBatch` manages its own `VertexArray`, `VertexBuffer`, `Shader`, and
batching logic. The ECS and `RawRenderableEntity` are no longer involved in normal 2D
rendering (though they remain available for advanced use cases).

**Batching strategy:** Collect quads into a CPU-side float buffer during `draw()` calls,
upload and flush on `end()` (or when the batch is full).

---

### Step 6 — Refactor Input to Snapshot-Based API

**What:** Replace the static, GLFW-key-code-based `KeyListener` / `MouseListener` with a
snapshot model matching MonoGame's `Keyboard` / `Mouse` / `GamePad` classes.

**New classes:**

```java
// hu.mudlee.core.input
public final class Keyboard {
    public static KeyboardState getState();
}

public final class KeyboardState {
    public boolean isKeyDown(Keys key);
    public boolean isKeyUp(Keys key);
}

public enum Keys {
    ESCAPE, A, B, C, ... UP, DOWN, LEFT, RIGHT, SPACE, ENTER, ...
}

public final class Mouse {
    public static MouseState getState();
}

public final class MouseState {
    public int x();
    public int y();
    public boolean isLeftButtonDown();
    public boolean isRightButtonDown();
    public int scrollWheelValue();
}
```

`Keys` maps internally to GLFW key codes; GLFW never leaks into game code.

The old static `KeyListener` / `MouseListener` are retired from the public API.

---

### Step 7 — Introduce `Color`

**What:** Replace raw `Vector4f` for colours everywhere with a typed `Color` value class,
matching MonoGame's `Color`.

**New class:** `hu.mudlee.core.Color`

```java
public final class Color {
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);
    public static final Color BLACK = new Color(0f, 0f, 0f, 1f);
    public static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);
    // ... standard palette constants

    public Color(float r, float g, float b, float a);
    public Color(int r, int g, int b, int a);  // 0–255

    public Vector4f toVector4f();
}
```

**Touches:** `Renderer.setClearColor()`, `GraphicsDevice.clear()`, `SpriteBatch.draw()`.

---

### Step 8 — Refactor Camera to Integrate with `SpriteBatch`

**What:** Camera no longer manually pushes matrices into shaders. Instead, its transform
matrix is passed into `SpriteBatch.begin()`, matching MonoGame's pattern.

**Changes to `Camera2D`:**

```java
public class Camera2D {
    public Vector2f position;
    public float zoom;
    public float rotation;

    public Camera2D(Viewport viewport);
    public Matrix4f getTransformMatrix(); // passed to SpriteBatch.begin()
}
```

Remove `getProjectionMatrix()` / `getViewMatrix()` from the public API (they become
internal). Manual uniform uploads disappear from game code entirely.

---

### Step 9 — Optional `Screen` / Screen Stack (replaces `Scene`)

**What:** Introduce an optional `Screen` abstraction for games that need multiple scenes
(menus, gameplay, pause). This is separate from the `Game` lifecycle — the `Game` itself
manages screens via a `ScreenManager` component.

```java
public interface Screen {
    void show();                     // replaces start()
    void update(GameTime gameTime);
    void draw(GameTime gameTime);
    void resize(int width, int height);
    void hide();
    void dispose();
}

public class ScreenManager extends GameComponent {
    public void push(Screen screen);
    public void pop();
    public void set(Screen screen);
}
```

`GameComponent` follows MonoGame's pattern — components are attached to `Game` and
participate in the `update` / `draw` loop automatically.

---

### Step 10 — Introduce `GraphicsDevice` Public API

**What:** Surface a `GraphicsDevice` object (already internal) as the primary interface to
the GPU, replacing the static `Renderer` facade in public-facing code.

```java
public class GraphicsDevice {
    public void clear(Color color);
    public Viewport getViewport();
    public void setViewport(Viewport viewport);
    public RenderBackend getBackend();  // OPENGL or VULKAN
}
```

`Renderer` stays as the internal implementation driver; `GraphicsDevice` is what game code
holds a reference to via `Game.graphicsDevice`.

---

## Execution Order Summary

| Step | What | Current Removed | New Introduced |
|------|------|-----------------|----------------|
| 1 | `GameTime` | `float deltaTime` | `GameTime` |
| 2 | `Game` class | `Application`, public `Scene`, `SceneManager` | `Game` |
| 3 | `GraphicsDeviceManager` | `WindowPreferences` | `GraphicsDeviceManager` |
| 4 | `ContentManager` | `Texture2D.create()` as public API | `ContentManager` |
| 5 | `SpriteBatch` | Manual VAO/VBO/ECS for 2D | `SpriteBatch` |
| 6 | Input snapshot API | `KeyListener`, `MouseListener` | `Keyboard`, `Mouse`, `Keys` |
| 7 | `Color` | `Vector4f` for colour params | `Color` |
| 8 | Camera integration | Manual uniform uploads | `Camera2D.getTransformMatrix()` |
| 9 | `Screen` / `ScreenManager` | (optional, augments Step 2) | `Screen`, `ScreenManager` |
| 10 | `GraphicsDevice` public API | Static `Renderer` in public API | `GraphicsDevice` |

Steps 1–4 are foundational and should be done in order. Steps 5–10 can proceed in parallel
once the foundation is in place.
