# Plan 003 — Unity-like GameObject & Component System

## Objective

Introduce a lightweight `GameObject` + `Component` system into the engine so that game
objects (player, enemies, props) are assembled by composing reusable components rather
than hand-coding monolithic `Screen` logic. The target API mirrors Unity's style in Java.

## Target API (Goal)

```java
// PlayerScreen.show()
var sheet = new SpriteSheet(texture, 48, 48);
var player = new GameObject("Player");
player.transform.position.set(960, 540);
player.addComponent(new SpriteRenderer())
      .addComponent(new Animator()
          .add("IdleRight",  sheet.createAnimation(...))
          .add("WalkRight",  sheet.createAnimation(...)))
      .addComponent(new PlayerController());
player.start();

// PlayerScreen.update()
player.update(gameTime);

// PlayerScreen.draw()
spriteBatch.begin(camera.getTransformMatrix());
player.draw(gameTime, spriteBatch);
spriteBatch.end();
```

---

## What NOT to change

- `GameComponent` (engine-level loop component, e.g. `ScreenManager`) — unaffected.
- `Screen` interface — unaffected.
- `SpriteBatch`, `AnimationPlayer`, `Animation`, `SpriteSheet` — unaffected.
- The existing `PlayerScreen` remains the sandbox screen; only its internals are refactored.

---

## New Packages

```
hu.mudlee.core.gameobject             → Component, Transform, GameObject
hu.mudlee.core.gameobject.components  → SpriteRenderer, Animator (built-in)
hu.mudlee.sandbox                     → PlayerController (game-specific behavior)
```

---

## Step 1 — `Component` abstract class

**File:** `core/.../gameobject/Component.java`

```java
public abstract class Component {
    GameObject gameObject;   // set by GameObject.addComponent()

    public void start() {}
    public void update(GameTime gameTime) {}
    public void draw(GameTime gameTime, SpriteBatch batch) {}
    public void dispose() {}

    protected <T extends Component> T getComponent(Class<T> type) {
        return gameObject.getComponent(type);
    }
}
```

`gameObject` is package-private; only `GameObject` assigns it.

---

## Step 2 — `Transform` component

**File:** `core/.../gameobject/Transform.java`

```java
public final class Transform extends Component {
    public final Vector2f position = new Vector2f();
    public float rotation = 0f;
    public final Vector2f scale = new Vector2f(1f, 1f);
}
```

`Transform` is always present on every `GameObject` — it is not optional.

---

## Step 3 — `GameObject`

**File:** `core/.../gameobject/GameObject.java`

```java
public final class GameObject {
    public final String name;
    public final Transform transform = new Transform();

    private final List<Component> components = new ArrayList<>();

    public GameObject(String name) { ... }

    public GameObject addComponent(Component component) {
        component.gameObject = this;
        components.add(component);
        return this;  // fluent chaining
    }

    public <T extends Component> T getComponent(Class<T> type) {
        for (var c : components) {
            if (type.isInstance(c)) { return type.cast(c); }
        }
        return null;
    }

    public void start()  { transform.gameObject = this; for (var c : components) { c.start(); } }
    public void update(GameTime gt) { for (var c : components) { c.update(gt); } }
    public void draw(GameTime gt, SpriteBatch b) { for (var c : components) { c.draw(gt, b); } }
    public void dispose() { for (var c : components) { c.dispose(); } }
}
```

`transform.gameObject` is set in `start()` so it is ready before any `Component.start()` runs.

---

## Step 4 — `SpriteRenderer` built-in component

**File:** `core/.../gameobject/components/SpriteRenderer.java`

Renders the current `TextureRegion`. If an `Animator` is present on the same object it
reads the frame from it; otherwise it uses the statically assigned region.

```java
public final class SpriteRenderer extends Component {
    private TextureRegion region;
    public Color color = Color.WHITE;
    public float scale = 1f;
    public boolean flipX, flipY;

    public SpriteRenderer setRegion(TextureRegion r) { this.region = r; return this; }

    @Override
    public void draw(GameTime gameTime, SpriteBatch batch) {
        var animator = getComponent(Animator.class);
        var frame = (animator != null) ? animator.getCurrentFrame() : region;
        if (frame == null) { return; }
        var t = gameObject.transform;
        batch.draw(frame, t.position, color, t.rotation,
                   new Vector2f(), scale * t.scale.x, flipX, flipY);
    }
}
```

---

## Step 5 — `Animator` built-in component

**File:** `core/.../gameobject/components/Animator.java`

Wraps `AnimationPlayer` and a name-keyed map of `Animation` instances.

```java
public final class Animator extends Component {
    private final AnimationPlayer player = new AnimationPlayer();
    private final Map<String, Animation> clips = new LinkedHashMap<>();

    public Animator add(String name, Animation clip) { clips.put(name, clip); return this; }

    public void play(String name) {
        var clip = clips.get(name);
        if (clip != null) { player.play(clip); }
    }

    public TextureRegion getCurrentFrame() { return player.getCurrentFrame(); }
    public boolean isFinished() { return player.isFinished(); }

    @Override
    public void update(GameTime gameTime) { player.update(gameTime); }
}
```

`Animator.update()` is called automatically by `GameObject.update()`, so game code never
calls `player.update()` directly.

---

## Step 6 — `PlayerController` behavior component (sandbox)

**File:** `sandbox/.../PlayerController.java`

Extracts all movement and state-machine logic from `PlayerScreen` into a self-contained
component. Replaces the inline `Direction`/`State` enums and the switch blocks.

```java
public final class PlayerController extends Component {

    private enum Direction { DOWN, RIGHT, UP, LEFT }
    private enum State     { IDLE, WALK, ATTACK, DIE }

    private static final float MOVE_SPEED = 300f;

    private Direction direction = Direction.RIGHT;
    private State state = State.IDLE;

    @Override
    public void update(GameTime gameTime) {
        var animator = getComponent(Animator.class);
        var renderer = getComponent(SpriteRenderer.class);
        var transform = gameObject.transform;
        var ks = Keyboard.getState();
        var dt = gameTime.elapsedSeconds();

        // state machine + movement identical to current PlayerScreen.update(),
        // but operating on animator.play(name) and transform.position instead
        // of inline animation fields and position Vector2f.
    }
}
```

The state-machine body is a direct transplant of the existing `PlayerScreen.update()` code,
replacing `playerAnimation.play(X)` with `animator.play("X")` and `position.x` with
`transform.position.x`. The `SpriteRenderer.flipX` flag is set here instead of inline in
the draw call.

---

## Step 7 — Simplify `PlayerScreen`

Remove `Direction`, `State`, all `Animation` fields, and the movement logic from
`PlayerScreen`. The screen becomes a thin coordinator:

```java
public class PlayerScreen implements Screen {
    private static final float SCALE = 8f;

    private final Game game;
    private final GraphicsDevice graphicsDevice;

    private ContentManager content;
    private SpriteBatch spriteBatch;
    private Camera2D camera;
    private GameObject player;

    @Override
    public void show() {
        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet(texture, 48, 48);

        player = new GameObject("Player");
        player.transform.position.set(960, 540);
        player.addComponent(new SpriteRenderer().setScale(SCALE))
              .addComponent(new Animator()
                  .add("IdleDown",    sheet.createAnimation("IdleDown",    0, 0, 6, 0.12f, PlayMode.LOOP))
                  // … remaining clips …
                  .add("Die",         sheet.createAnimation("Die",         9, 0, 3, 0.20f, PlayMode.ONCE)))
              .addComponent(new PlayerController());
        player.start();

        spriteBatch = new SpriteBatch();
        camera = new OrthographicCamera();

        var actions = new InputActionMap("Player");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> game.exit());
        actions.enable();
    }

    @Override
    public void update(GameTime gameTime) {
        player.update(gameTime);
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        spriteBatch.begin(camera.getTransformMatrix());
        player.draw(gameTime, spriteBatch);
        spriteBatch.end();
    }

    @Override
    public void dispose() {
        player.dispose();
        spriteBatch.dispose();
        content.unload();
    }
}
```

---

## Step 8 — Export new packages in `module-info.java`

Add to `core/src/main/java/module-info.java`:

```java
exports hu.mudlee.core.gameobject;
exports hu.mudlee.core.gameobject.components;
```

---

## Execution Order

| Step | What | Files |
|------|------|-------|
| 1 | `Component` abstract base | `core/.../gameobject/Component.java` |
| 2 | `Transform` | `core/.../gameobject/Transform.java` |
| 3 | `GameObject` | `core/.../gameobject/GameObject.java` |
| 4 | `SpriteRenderer` | `core/.../gameobject/components/SpriteRenderer.java` |
| 5 | `Animator` | `core/.../gameobject/components/Animator.java` |
| 6 | `PlayerController` | `sandbox/.../PlayerController.java` |
| 7 | Refactor `PlayerScreen` | `sandbox/.../PlayerScreen.java` |
| 8 | Export new packages | `core/module-info.java` |

Steps 1–3 are sequential (each depends on the previous).
Steps 4 and 5 can be done in parallel after Step 1.
Steps 6 and 7 require Steps 1–5.
Step 8 can be done after Step 3 (as soon as the packages exist).
