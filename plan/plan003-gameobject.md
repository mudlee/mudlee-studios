# Plan 003 — Unity-like GameObject & Component System

## Objective

Introduce a Unity-style GameObject and Component architecture so that game entities are
composed from reusable, single-responsibility components rather than monolithic screen
classes. The current `PlayerScreen` is the concrete refactoring target: its sprite loading,
animation, movement, state machine, and rendering concerns should each become a distinct
component attached to a player `GameObject`.

## Reference

- Unity GameObject/Component: https://docs.unity3d.com/Manual/GameObjects.html
- Unity Component lifecycle: https://docs.unity3d.com/Manual/ExecutionOrder.html
- MonoGame DrawableGameComponent (existing pattern in this engine): https://docs.monogame.net/api/Microsoft.Xna.Framework.DrawableGameComponent.html
- Current `PlayerScreen` — the monolith to decompose

---

## The Problem: Monolithic Screens

Today all entity logic lives inside `Screen` implementations. `PlayerScreen` owns sprite
loading, 10 animations, an `AnimationPlayer`, movement input, a state machine, and
`SpriteBatch` draw calls — all in a single 196-line class. Adding a second entity (an enemy,
an NPC) means duplicating all of this inside the same screen or creating parallel helper
classes with no common lifecycle contract.

There is no concept of a "game object" that owns a transform, no way to compose behaviours
by attaching/removing components, and no scene-level iteration over entities.

---

## What the Target API Looks Like (Goal)

```java
public class PlayerScene extends GameScene {

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        super(game, graphicsDevice);
    }

    @Override
    public void onShow() {
        var content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet(texture, 48, 48);

        var player = new GameObject("Player");
        player.transform.position.set(960, 540);

        var animator = new Animator();
        animator.addAnimation("IdleDown",    sheet.createAnimation("IdleDown",    0, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("IdleRight",   sheet.createAnimation("IdleRight",   1, 0, 6, 0.12f, PlayMode.LOOP));
        // ... remaining animations ...
        animator.play("IdleRight");
        player.addComponent(animator);

        var spriteRenderer = new SpriteRenderer();
        spriteRenderer.scale = 8f;
        player.addComponent(spriteRenderer);

        player.addComponent(new PlayerController(300f));

        addGameObject(player);
    }
}
```

```java
public class PlayerController extends Component {

    private final float moveSpeed;

    public PlayerController(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    @Override
    public void update(GameTime gameTime) {
        var ks = Keyboard.getState();
        var dt = gameTime.elapsedSeconds();
        var transform = gameObject.transform;
        var animator = gameObject.getComponent(Animator.class);

        if (ks.isKeyDown(Keys.RIGHT)) {
            transform.position.x += moveSpeed * dt;
        }
        // ... movement, state transitions, animator.play() calls ...
    }
}
```

---

## Transformation Steps

### Step 1 — Introduce `Component` Base Class

**What:** An abstract base class for all entity-level components. Completely separate from
the existing `GameComponent` (which is a game-level loop participant attached to `Game`,
not to an individual entity).

**New class:** `hu.mudlee.core.gameobject.Component`

A `Component` has a back-reference to its owning `GameObject` (set by the `GameObject` when
the component is added), an enabled flag, and lifecycle methods that mirror Unity's execution
order in a simplified form:

- `awake()` — called once when the component is added to a `GameObject`
- `start()` — called once before the first `update()`, after all components on the
  `GameObject` have been awakened (deferred to the first scene update)
- `update(GameTime)` — called every frame
- `draw(GameTime)` — called every frame after all updates
- `dispose()` — called when the `GameObject` is removed from the scene or destroyed

All lifecycle methods have empty default implementations. Subclasses override only what they
need.

The `gameObject` field is package-private and set by `GameObject.addComponent()`. A public
`getGameObject()` accessor is provided. Helper method `getComponent(Class<T>)` delegates to
`gameObject.getComponent(Class<T>)` for convenient sibling component access.

**Why first:** Every subsequent step depends on this base type.

---

### Step 2 — Introduce `Transform`

**What:** A built-in component that every `GameObject` always has. Holds the entity's 2D
spatial state.

**New class:** `hu.mudlee.core.gameobject.Transform` (extends `Component`)

Fields (all public, mutable, matching JOML types to avoid per-frame allocation):

- `position` — `Vector2f`, world position
- `rotation` — `float`, radians
- `scale` — `Vector2f`, defaults to `(1, 1)`

`Transform` overrides no lifecycle methods — it is pure data. Other components read/write it
directly via `gameObject.transform`.

**Why before `GameObject`:** `GameObject` creates its `Transform` in the constructor, so
`Transform` must exist first.

---

### Step 3 — Introduce `GameObject`

**What:** A named container of `Component` instances. Owns a `Transform` that is always
present and cannot be removed.

**New class:** `hu.mudlee.core.gameobject.GameObject`

```java
public final class GameObject {
    public final String name;
    public final Transform transform;
    public boolean enabled = true;

    public GameObject(String name);

    public <T extends Component> T addComponent(T component);
    public <T extends Component> T getComponent(Class<T> type);
    public <T extends Component> boolean hasComponent(Class<T> type);
    public void removeComponent(Component component);
}
```

Internal component storage is an `ArrayList<Component>` (pre-sized to 8). Type lookup in
`getComponent` does a linear scan — fine for the expected component count per entity (< 10).

`addComponent`:
1. Sets `component.gameObject` to `this`
2. Adds to the internal list
3. Calls `component.awake()`

`removeComponent`:
1. Calls `component.dispose()`
2. Removes from the list
3. Nulls `component.gameObject`

`GameObject` itself is not a `Component` — it is the container. It exposes package-private
`updateComponents(GameTime)` and `drawComponents(GameTime)` methods that the scene calls.
These iterate the component list and call `update` / `draw` on each enabled component.
On the first update, any component whose `start()` has not yet been called gets `start()`
invoked before `update()`.

A boolean flag `started` on `Component` tracks whether `start()` has run. This is checked at
the beginning of `GameObject.updateComponents()`.

**Why:** The core building block that everything else attaches to.

---

### Step 4 — Introduce `GameScene`

**What:** A `Screen` implementation that manages a flat list of `GameObject` instances and
drives their lifecycle. It integrates with the existing `ScreenManager` — you push/set a
`GameScene` just like any other `Screen`.

**New class:** `hu.mudlee.core.gameobject.GameScene`

```java
public abstract class GameScene implements Screen {
    protected final Game game;
    protected final GraphicsDevice graphicsDevice;
    protected Camera2D camera;
    protected SpriteBatch spriteBatch;

    protected GameScene(Game game, GraphicsDevice graphicsDevice);

    protected abstract void onShow();

    protected void addGameObject(GameObject go);
    protected void removeGameObject(GameObject go);
}
```

`GameScene` owns:
- An `ArrayList<GameObject>` of active game objects
- A `SpriteBatch` (created once in `show()`)
- A `Camera2D` (defaults to `OrthographicCamera`, overridable)
- A reusable `ArrayList<SpriteRenderer>` for draw-phase collection (avoids allocation)

Lifecycle wiring — `GameScene` implements `Screen` methods:

- `show()` — creates the `SpriteBatch` and camera, then calls the abstract `onShow()` where
  subclasses set up their GameObjects
- `update(GameTime)` — iterates all enabled GameObjects, calls `updateComponents(gameTime)`
  on each
- `draw(GameTime)` — calls `graphicsDevice.clear()`, collects all `SpriteRenderer`
  components from active GameObjects into the reusable list, sorts by `drawOrder`, calls
  `spriteBatch.begin()`, iterates and draws, calls `spriteBatch.end()`
- `dispose()` — iterates all GameObjects, calls `dispose()` on every component, disposes
  the `SpriteBatch`

`addGameObject` appends to the list. `removeGameObject` disposes all components on the
object and removes it.

**Draw ordering:** `SpriteRenderer` has an `int drawOrder` field (default 0). Lower values
draw first (back to front). The scene sorts the reusable renderer list by `drawOrder` before
the draw pass. For a small number of entities, `ArrayList.sort()` with a cached `Comparator`
is sufficient.

**Why:** This is the orchestrator. Without it, GameObjects have no frame driver.

---

### Step 5 — Introduce `SpriteRenderer` Component

**What:** A component that knows how to render a single sprite or animation frame via
`SpriteBatch`. It replaces the manual `spriteBatch.draw(...)` calls currently in
`PlayerScreen.draw()`.

**New class:** `hu.mudlee.core.gameobject.SpriteRenderer` (extends `Component`)

Public fields:

- `TextureRegion region` — the current frame to render (may be set directly or populated
  by `Animator`)
- `Color color` — tint, defaults to `Color.WHITE`
- `float scale` — uniform scale multiplier, defaults to `1f`
- `Vector2f origin` — rotation/scale origin in local pixels, defaults to `(0, 0)`
- `boolean flipX`, `boolean flipY` — horizontal/vertical flip
- `int drawOrder` — z-ordering for the scene's draw pass

`SpriteRenderer` does not call `SpriteBatch` itself. Instead, `GameScene.draw()` reads
these fields and issues the draw call. This keeps the rendering centralised in the scene
(single `begin/end` pair) and avoids each component needing a `SpriteBatch` reference.

If an `Animator` component is present on the same `GameObject`, `SpriteRenderer` reads
`animator.getCurrentFrame()` during the draw phase (the `GameScene` does this wiring —
see Step 6). If no `Animator` is present, `SpriteRenderer.region` is used as-is.

**Why:** Separates "what to render" from "game logic." Any component can modify
`SpriteRenderer` fields (flip, color, scale) without touching draw calls.

---

### Step 6 — Introduce `Animator` Component

**What:** A component that manages named `Animation` instances and drives an
`AnimationPlayer`. Replaces the animation setup and state-time tracking currently
scattered across `PlayerScreen`.

**New class:** `hu.mudlee.core.gameobject.Animator` (extends `Component`)

```java
public final class Animator extends Component {
    public Animator();

    public void addAnimation(String name, Animation animation);
    public void play(String name);
    public TextureRegion getCurrentFrame();
    public boolean isFinished();
}
```

Internally holds a `HashMap<String, Animation>` and an `AnimationPlayer`. The `update()`
override advances the `AnimationPlayer`. `play(name)` looks up the animation and delegates
to `AnimationPlayer.play()` — if the same name is already playing, it's a no-op (existing
`AnimationPlayer` behaviour).

`getCurrentFrame()` delegates to `AnimationPlayer.getCurrentFrame()`.

The `HashMap` lookup in `play()` happens only on animation transitions (not every frame),
so the per-frame path is just `AnimationPlayer.update()`.

**Why:** Encapsulates all animation concern. A `PlayerController` component just calls
`animator.play("WalkRight")` without touching `AnimationPlayer` internals.

---

### Step 7 — Refactor `PlayerScreen` into a `GameScene` + Components

**What:** Decompose `PlayerScreen` into:

1. **`PlayerScene extends GameScene`** — creates the player `GameObject`, loads assets,
   sets up the camera. Lives in the sandbox module.

2. **`PlayerController extends Component`** — lives in the sandbox module. Handles input,
   direction, state machine (IDLE, WALK, ATTACK, DIE), and drives the `Animator` and
   `SpriteRenderer` flip accordingly. All the `Direction` and `State` enums move here.

The asset loading (ContentManager, SpriteSheet, animation creation) stays in `PlayerScene.onShow()` —
it is scene setup, not per-frame component logic.

**Skeleton `PlayerScene`:**

```java
public class PlayerScene extends GameScene {

    private ContentManager content;
    private InputActionMap actions;

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        super(game, graphicsDevice);
    }

    @Override
    protected void onShow() {
        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet(texture, 48, 48);

        var player = new GameObject("Player");
        player.transform.position.set(960, 540);

        var animator = new Animator();
        animator.addAnimation("IdleDown",     sheet.createAnimation("IdleDown",     0, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("IdleRight",    sheet.createAnimation("IdleRight",    1, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("IdleUp",       sheet.createAnimation("IdleUp",       2, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("WalkDown",     sheet.createAnimation("WalkDown",     3, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("WalkRight",    sheet.createAnimation("WalkRight",    4, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("WalkUp",       sheet.createAnimation("WalkUp",       5, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("AttackDown",   sheet.createAnimation("AttackDown",   6, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("AttackRight",  sheet.createAnimation("AttackRight",  7, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("AttackUp",     sheet.createAnimation("AttackUp",     8, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("Die",          sheet.createAnimation("Die",          9, 0, 3, 0.20f, PlayMode.ONCE));
        animator.play("IdleRight");
        player.addComponent(animator);

        var spriteRenderer = new SpriteRenderer();
        spriteRenderer.scale = 8f;
        player.addComponent(spriteRenderer);

        player.addComponent(new PlayerController(300f));

        addGameObject(player);

        actions = new InputActionMap("Player");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> game.exit());
        actions.enable();
    }

    @Override
    public void dispose() {
        actions.disable();
        content.unload();
        super.dispose();
    }
}
```

**Skeleton `PlayerController`:**

```java
public class PlayerController extends Component {

    private final float moveSpeed;
    private Direction direction = Direction.RIGHT;
    private State state = State.IDLE;

    public PlayerController(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    @Override
    public void update(GameTime gameTime) {
        var ks = Keyboard.getState();
        var dt = gameTime.elapsedSeconds();
        var transform = gameObject.transform;
        var animator = getComponent(Animator.class);
        var sr = getComponent(SpriteRenderer.class);

        if (state == State.DIE) {
            return;
        }
        if (state == State.ATTACK) {
            if (animator.isFinished()) {
                state = State.IDLE;
            }
            return;
        }
        if (ks.isKeyDown(Keys.X)) {
            state = State.DIE;
            animator.play("Die");
            return;
        }
        if (ks.isKeyDown(Keys.SPACE)) {
            state = State.ATTACK;
            animator.play(attackAnimationFor(direction));
            return;
        }

        var moving = false;
        if (ks.isKeyDown(Keys.RIGHT)) {
            transform.position.x += moveSpeed * dt;
            direction = Direction.RIGHT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.LEFT)) {
            transform.position.x -= moveSpeed * dt;
            direction = Direction.LEFT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.DOWN)) {
            transform.position.y -= moveSpeed * dt;
            direction = Direction.DOWN;
            moving = true;
        }
        if (ks.isKeyDown(Keys.UP)) {
            transform.position.y += moveSpeed * dt;
            direction = Direction.UP;
            moving = true;
        }

        state = moving ? State.WALK : State.IDLE;
        animator.play(animationFor(state, direction));
        sr.flipX = (direction == Direction.LEFT);
    }

    // ... private Direction/State enums and helper methods ...
}
```

**`SandboxApplication` change:**

```java
screenManager.set(new PlayerScene(this, graphicsDevice));
```

**`module-info.java` change:**

```java
exports hu.mudlee.core.gameobject;
```

---

## Execution Order Summary

| Step | What | New Introduced |
|------|------|----------------|
| 1 | `Component` base class | Abstract lifecycle base for entity-level components |
| 2 | `Transform` | Position, rotation, scale — always present on a GameObject |
| 3 | `GameObject` | Named component container with add/get/remove and lifecycle dispatch |
| 4 | `GameScene` | Screen implementation that drives GameObjects each frame |
| 5 | `SpriteRenderer` | Declarative render state (region, color, scale, flip, drawOrder) |
| 6 | `Animator` | Named animation map + AnimationPlayer wrapper |
| 7 | Refactor `PlayerScreen` | `PlayerScene` + `PlayerController` using all new types |

Steps 1–3 must be done in order (each depends on the previous). Step 4 depends on Steps 1–3.
Steps 5 and 6 can be done in parallel once Step 1 exists. Step 7 requires all prior steps.

