# Plan 009 — Migrate to a Full ECS

Replaces the current `GameObject`/`Component` system with a production-grade ECS.
Modelled after *"Designing a Production-Grade ECS — Java Implementation"* (Ankit Kumar Srivastava).

---

## What Changes and Why

### Current system

```
GameScene2D
  └─ List<GameObject>
       └─ GameObject (name + Transform2D field)
            └─ List<Component>   ← abstract class with start/update/draw/dispose
                 ├─ SpriteRenderer2D  ← logic + data mixed
                 ├─ Animator2D        ← logic + data mixed
                 └─ PlayerController  ← logic + data mixed, state as fields
```

Problems:
- Components carry both **data** and **behaviour** — violates SRP.
- `SpriteRenderer2D.draw()` casts `RenderContext` to `SpriteBatch2D` internally — rendering
  logic buried inside the component tree.
- `PlayerController` holds mutable `direction`/`state` fields — mixing state into logic.
  Two players would need two `PlayerController` instances, not two entities.
- `GameObject.getComponent()` is a linear scan every time a sibling is read.
- `Transform2D` hardcoded on `GameObject` blocks 3D migration (plan006 §3).

### Target system

```
World
  ├─ EntityManager                  ← all entity/component data
  ├─ SystemManager                  ← ordered update + render systems
  │    ├─ PlayerControlSystem       ← stateless logic
  │    ├─ Animation2DSystem         ← advances players, writes frame to Sprite2DComponent
  │    └─ SpriteRender2DSystem      ← reads region, issues draw calls
  └─ (entity factory inline in scene)
```

- **Entity** = `record Entity(int id)` — type-safe, not a raw `int`.
- **Component** = marker `interface Component` — pure data, zero lifecycle.
- **System** = stateless class; all mutable per-entity state lives in components.
- **Camera** = a proper entity carrying a `CameraComponent` — not a hardcoded field.

---

## What Is NOT Changed

- `SpriteBatch2D`, `AnimationPlayer2D`, `Animation2D`, `TextureRegion`
- `Camera2D` / `OrthographicCamera2D`
- `Screen`, `ScreenManager`, `Game`, `GraphicsDevice`
- `ContentManager`, `InputActionMap`, rendering back-end (OpenGL/Vulkan)
- `SpriteSheet2D`, `Texture2D`

---

## New Package Layout

```
core/src/main/java/hu/mudlee/core/
    ecs/
        Entity.java                      step 1
        Component.java                   step 2
        EntityManager.java               step 3
        SystemBase.java                  step 4
        RenderSystemBase.java            step 4
        SystemManager.java               step 4
        World.java                       step 5
        ECSScene2D.java                  step 8
    ecs/component/
        Transform2DComponent.java        step 6
        Sprite2DComponent.java           step 6
        Animation2DComponent.java        step 6
        VelocityComponent.java           step 6
        CameraComponent.java             step 6
    ecs/system/
        Animation2DSystem.java           step 7
        SpriteRender2DSystem.java        step 7

sandbox/src/main/java/hu/mudlee/sandbox/
    PlayerStateComponent.java            step 9
    PlayerControlSystem.java             step 9
    PlayerScene.java                     step 9  (rewrite)
```

Deleted in step 10:
```
core/.../gameobject/Component.java
core/.../gameobject/GameObject.java
core/.../gameobject/Transform2D.java
core/.../gameobject/GameScene2D.java
core/.../gameobject/components/SpriteRenderer2D.java
core/.../gameobject/components/Animator2D.java
sandbox/.../PlayerController.java
```

---

## Step 1 — `Entity` record

```java
package hu.mudlee.core.ecs;

public record Entity(int id) {}
```

Type-safe wrapper. Prevents raw-int misuse and works correctly as a `Map` key.

---

## Step 2 — `Component` marker interface

```java
package hu.mudlee.core.ecs;

public interface Component {}
```

Replaces the abstract `Component` class in `hu.mudlee.core.gameobject`.

---

## Step 3 — `EntityManager`

Dual-index storage + query result cache to avoid per-frame allocation.

```java
package hu.mudlee.core.ecs;

import java.util.*;

public final class EntityManager {

    private int nextId = 0;

    // type → (entityId → component)
    private final Map<Class<? extends Component>, Map<Integer, Component>> byType = new HashMap<>();
    // entityId → set of component types (for fast full destroy)
    private final Map<Integer, Set<Class<? extends Component>>> byEntity = new HashMap<>();
    // query cache: component-type set → cached result list
    private final Map<Set<Class<? extends Component>>, List<Entity>> queryCache = new HashMap<>();

    public Entity createEntity() {
        var e = new Entity(nextId++);
        byEntity.put(e.id(), new HashSet<>());
        return e;
    }

    public void destroyEntity(Entity entity) {
        var types = byEntity.remove(entity.id());
        if (types != null) {
            for (var type : types) { byType.get(type).remove(entity.id()); }
        }
        queryCache.clear();
    }

    public <T extends Component> void addComponent(Entity entity, T component) {
        byType.computeIfAbsent(component.getClass(), k -> new HashMap<>())
              .put(entity.id(), component);
        byEntity.get(entity.id()).add(component.getClass());
        queryCache.clear();
    }

    public void removeComponent(Entity entity, Class<? extends Component> type) {
        var map = byType.get(type);
        if (map != null) { map.remove(entity.id()); }
        var types = byEntity.get(entity.id());
        if (types != null) { types.remove(type); }
        queryCache.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Entity entity, Class<T> type) {
        var map = byType.get(type);
        return map == null ? null : (T) map.get(entity.id());
    }

    public boolean hasComponent(Entity entity, Class<? extends Component> type) {
        var map = byType.get(type);
        return map != null && map.containsKey(entity.id());
    }

    /**
     * Returns entities that have ALL required component types.
     * Result is cached and rebuilt only when the entity set changes.
     */
    @SafeVarargs
    public final List<Entity> getEntitiesWith(Class<? extends Component>... required) {
        var key = new HashSet<Class<? extends Component>>(Arrays.asList(required));
        return queryCache.computeIfAbsent(key, k -> buildQuery(required));
    }

    @SafeVarargs
    private List<Entity> buildQuery(Class<? extends Component>... required) {
        Set<Integer> ids = null;
        for (var type : required) {
            var map = byType.get(type);
            if (map == null) { return List.of(); }
            if (ids == null) {
                ids = new HashSet<>(map.keySet());
            } else {
                ids.retainAll(map.keySet());
            }
            if (ids.isEmpty()) { return List.of(); }
        }
        if (ids == null) { return List.of(); }
        var result = new ArrayList<Entity>(ids.size());
        for (var id : ids) { result.add(new Entity(id)); }
        return Collections.unmodifiableList(result);
    }
}
```

**GC note (plan007):** `getEntitiesWith` allocates a `HashSet` for the cache lookup key
on every call. This is unavoidable with a `Map` key, but the result list is reused across
frames. The only per-frame allocations here are the small key `HashSet` (4–8 classes) and
no result `List`. Eliminate this completely in a future step by pre-registering query
signatures as constants with an `int` ID.

---

## Step 4 — `SystemBase`, `RenderSystemBase`, `SystemManager`

Systems that issue draw calls extend `RenderSystemBase`. `SystemManager` routes them
into separate lists so `World` can call update and render from the correct `Screen` phase.

```java
// SystemBase.java
package hu.mudlee.core.ecs;
import hu.mudlee.core.GameTime;

public abstract class SystemBase {
    protected final EntityManager em;
    protected SystemBase(EntityManager em) { this.em = em; }
    public void onStart() {}
    public abstract void update(GameTime gameTime);
    public void onDispose() {}
}

// RenderSystemBase.java
package hu.mudlee.core.ecs;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;

public abstract class RenderSystemBase extends SystemBase {
    protected RenderSystemBase(EntityManager em) { super(em); }
    public abstract void render(RenderContext context);
    @Override public final void update(GameTime gameTime) {}
}

// SystemManager.java
package hu.mudlee.core.ecs;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;
import java.util.ArrayList;
import java.util.List;

public final class SystemManager {
    private final List<SystemBase> updateSystems = new ArrayList<>();
    private final List<RenderSystemBase> renderSystems = new ArrayList<>();

    public void add(SystemBase system) {
        if (system instanceof RenderSystemBase r) { renderSystems.add(r); }
        else { updateSystems.add(system); }
    }

    public void updateAll(GameTime gameTime) {
        for (int i = 0; i < updateSystems.size(); i++) { updateSystems.get(i).update(gameTime); }
    }

    public void renderAll(RenderContext context) {
        for (int i = 0; i < renderSystems.size(); i++) { renderSystems.get(i).render(context); }
    }

    public void disposeAll() {
        for (int i = 0; i < updateSystems.size(); i++) { updateSystems.get(i).onDispose(); }
        for (int i = 0; i < renderSystems.size(); i++) { renderSystems.get(i).onDispose(); }
        updateSystems.clear();
        renderSystems.clear();
    }
}
```

---

## Step 5 — `World`

Façade that owns `EntityManager` and `SystemManager`.
`update()` is called from `Screen.update()`, `render()` from `Screen.draw()`.

```java
package hu.mudlee.core.ecs;
import hu.mudlee.core.Disposable;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;

public final class World implements Disposable {
    public final EntityManager entities = new EntityManager();
    private final SystemManager systems = new SystemManager();

    public void addSystem(SystemBase system) {
        system.onStart();
        systems.add(system);
    }

    public void update(GameTime gameTime) { systems.updateAll(gameTime); }
    public void render(RenderContext context) { systems.renderAll(context); }

    @Override
    public void dispose() { systems.disposeAll(); }
}
```

---

## Step 6 — Pure data components

All in `hu.mudlee.core.ecs.component`. No lifecycle methods. Public fields.

### `Transform2DComponent`
```java
public final class Transform2DComponent implements Component {
    public final Vector2f position = new Vector2f();
    public float rotation = 0f;
    public final Vector2f scale = new Vector2f(1f, 1f);
    public int z = 0; // sort layer: lower = drawn first (behind)
}
```

### `Sprite2DComponent`
```java
public final class Sprite2DComponent implements Component {
    public TextureRegion region; // written each frame by Animation2DSystem
    public Color tint = Color.WHITE;
    public float scale = 1f;
    public boolean flipX = false;
    public boolean flipY = false;
    public final Vector2f origin = new Vector2f();
}
```

### `Animation2DComponent`
```java
public final class Animation2DComponent implements Component {
    public final AnimationPlayer2D player = new AnimationPlayer2D();
    public final Map<String, Animation2D> clips = new LinkedHashMap<>();

    public Animation2DComponent add(String name, Animation2D clip) {
        clips.put(name, clip);
        return this;
    }

    public void play(String name) {
        var clip = clips.get(name);
        if (clip != null) { player.play(clip); }
    }
}
```

### `VelocityComponent`
```java
public final class VelocityComponent implements Component {
    public float dx = 0f;
    public float dy = 0f;
}
```

### `CameraComponent`
Camera is a first-class entity. `SpriteRender2DSystem` queries for the active one.
```java
public final class CameraComponent implements Component {
    public final Camera2D camera;
    public boolean active = true;

    public CameraComponent(Camera2D camera) { this.camera = camera; }
}
```

---

## Step 7 — `Animation2DSystem` and `SpriteRender2DSystem`

### `Animation2DSystem`

Advances the player each frame and **writes the current frame back into
`Sprite2DComponent.region`**. The render system only reads `region` — no branching needed.

```java
public final class Animation2DSystem extends SystemBase {
    public Animation2DSystem(EntityManager em) { super(em); }

    @Override
    public void update(GameTime gameTime) {
        for (var entity : em.getEntitiesWith(Animation2DComponent.class, Sprite2DComponent.class)) {
            var anim   = em.getComponent(entity, Animation2DComponent.class);
            var sprite = em.getComponent(entity, Sprite2DComponent.class);
            anim.player.update(gameTime);
            sprite.region = anim.player.getCurrentFrame();
        }
    }
}
```

### `SpriteRender2DSystem`

Sorts by `z`, then submits each renderable to `SpriteBatch2D`.
`sortBuffer` is a pre-allocated instance field — no allocation after the first frame.

```java
public final class SpriteRender2DSystem extends RenderSystemBase {
    private final List<Entity> sortBuffer = new ArrayList<>();

    public SpriteRender2DSystem(EntityManager em) { super(em); }

    @Override
    public void render(RenderContext context) {
        if (!(context instanceof SpriteBatch2D batch)) { return; }

        sortBuffer.clear();
        sortBuffer.addAll(em.getEntitiesWith(Transform2DComponent.class, Sprite2DComponent.class));
        sortBuffer.sort(Comparator.comparingInt(
            e -> em.getComponent(e, Transform2DComponent.class).z));

        for (int i = 0; i < sortBuffer.size(); i++) {
            var entity = sortBuffer.get(i);
            var t = em.getComponent(entity, Transform2DComponent.class);
            var s = em.getComponent(entity, Sprite2DComponent.class);
            if (s.region == null) { continue; }
            batch.draw(s.region, t.position, s.tint, t.rotation, s.origin,
                       s.scale * t.scale.x, s.flipX, s.flipY);
        }
    }
}
```

---

## Step 8 — `ECSScene2D`

Replaces `GameScene2D`. Owns a `World`. Creates the camera entity automatically.
`update()` → logic systems. `draw()` → clear + begin + render systems + end.

```java
package hu.mudlee.core.ecs;

public abstract class ECSScene2D implements Screen {
    protected final Game game;
    protected final GraphicsDevice graphicsDevice;
    protected final World world = new World();
    protected Color clearColor = Color.WHITE;

    private SpriteBatch2D spriteBatch;
    private Entity cameraEntity;

    protected ECSScene2D(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    protected abstract void onLoad();

    @Override
    public final void show() {
        spriteBatch = new SpriteBatch2D();
        cameraEntity = world.entities.createEntity();
        world.entities.addComponent(cameraEntity, new CameraComponent(new OrthographicCamera2D()));
        world.addSystem(new Animation2DSystem(world.entities));
        world.addSystem(new SpriteRender2DSystem(world.entities));
        onLoad();
    }

    protected Camera2D getCamera() {
        return world.entities.getComponent(cameraEntity, CameraComponent.class).camera;
    }

    @Override
    public void update(GameTime gameTime) { world.update(gameTime); }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(clearColor);
        spriteBatch.begin(getCamera().getTransformMatrix());
        world.render(spriteBatch);
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {}

    @Override
    public void dispose() {
        world.dispose();
        spriteBatch.dispose();
    }
}
```

---

## Step 9 — Rewrite sandbox

### `PlayerStateComponent`

Player mutable state extracted from the old `PlayerController` into pure data.
The system is now stateless — two players just need two entities with this component.

```java
package hu.mudlee.sandbox;
import hu.mudlee.core.ecs.Component;

public final class PlayerStateComponent implements Component {
    public Direction direction = Direction.RIGHT;
    public State state = State.IDLE;
    public float moveSpeed;

    public PlayerStateComponent(float moveSpeed) { this.moveSpeed = moveSpeed; }

    public enum Direction { DOWN, RIGHT, UP, LEFT }
    public enum State { IDLE, WALK, ATTACK, DIE }
}
```

### `PlayerControlSystem`

Stateless. Replaces `PlayerController` entirely.

```java
package hu.mudlee.sandbox;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.EntityManager;
import hu.mudlee.core.ecs.SystemBase;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;
import hu.mudlee.sandbox.PlayerStateComponent.Direction;
import hu.mudlee.sandbox.PlayerStateComponent.State;

public final class PlayerControlSystem extends SystemBase {

    public PlayerControlSystem(EntityManager em) { super(em); }

    @Override
    public void update(GameTime gameTime) {
        for (var entity : em.getEntitiesWith(PlayerStateComponent.class,
                Transform2DComponent.class, Sprite2DComponent.class, Animation2DComponent.class)) {
            var ps   = em.getComponent(entity, PlayerStateComponent.class);
            var t    = em.getComponent(entity, Transform2DComponent.class);
            var s    = em.getComponent(entity, Sprite2DComponent.class);
            var anim = em.getComponent(entity, Animation2DComponent.class);
            var ks   = Keyboard.getState();
            var dt   = gameTime.elapsedSeconds();

            if (ps.state == State.DIE) { return; }
            if (ps.state == State.ATTACK) {
                if (anim.player.isFinished()) { ps.state = State.IDLE; }
                return;
            }
            if (ks.isKeyDown(Keys.X)) { ps.state = State.DIE; anim.play("Die"); return; }
            if (ks.isKeyDown(Keys.SPACE)) { ps.state = State.ATTACK; anim.play(attackFor(ps.direction)); return; }

            var moving = false;
            if (ks.isKeyDown(Keys.RIGHT)) { t.position.x += ps.moveSpeed * dt; ps.direction = Direction.RIGHT; moving = true; }
            if (ks.isKeyDown(Keys.LEFT))  { t.position.x -= ps.moveSpeed * dt; ps.direction = Direction.LEFT;  moving = true; }
            if (ks.isKeyDown(Keys.DOWN))  { t.position.y -= ps.moveSpeed * dt; ps.direction = Direction.DOWN;  moving = true; }
            if (ks.isKeyDown(Keys.UP))    { t.position.y += ps.moveSpeed * dt; ps.direction = Direction.UP;    moving = true; }

            ps.state = moving ? State.WALK : State.IDLE;
            anim.play(animFor(ps.state, ps.direction));
            s.flipX = (ps.direction == Direction.LEFT);
        }
    }

    private String animFor(State s, Direction d) {
        return switch (s) {
            case IDLE   -> switch (d) { case DOWN -> "IdleDown"; case UP -> "IdleUp"; default -> "IdleRight"; };
            case WALK   -> switch (d) { case DOWN -> "WalkDown"; case UP -> "WalkUp"; default -> "WalkRight"; };
            case ATTACK -> attackFor(d);
            case DIE    -> "Die";
        };
    }

    private String attackFor(Direction d) {
        return switch (d) { case DOWN -> "AttackDown"; case UP -> "AttackUp"; default -> "AttackRight"; };
    }
}
```

### `PlayerScene` (rewrite)

```java
package hu.mudlee.sandbox;
import hu.mudlee.core.Game;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.ecs.ECSScene2D;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.animation.PlayMode;
import hu.mudlee.core.render.texture.SpriteSheet2D;
import hu.mudlee.core.render.texture.Texture2D;

public class PlayerScene extends ECSScene2D {
    private ContentManager content;
    private InputActionMap actions;

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        super(game, graphicsDevice);
    }

    @Override
    protected void onLoad() {
        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet   = new SpriteSheet2D(texture, 48, 48);

        var anim = new Animation2DComponent()
            .add("IdleDown",    sheet.createAnimation("IdleDown",    0, 0, 6, 0.12f, PlayMode.LOOP))
            .add("IdleRight",   sheet.createAnimation("IdleRight",   1, 0, 6, 0.12f, PlayMode.LOOP))
            .add("IdleUp",      sheet.createAnimation("IdleUp",      2, 0, 6, 0.12f, PlayMode.LOOP))
            .add("WalkDown",    sheet.createAnimation("WalkDown",    3, 0, 6, 0.08f, PlayMode.LOOP))
            .add("WalkRight",   sheet.createAnimation("WalkRight",   4, 0, 6, 0.08f, PlayMode.LOOP))
            .add("WalkUp",      sheet.createAnimation("WalkUp",      5, 0, 6, 0.08f, PlayMode.LOOP))
            .add("AttackDown",  sheet.createAnimation("AttackDown",  6, 0, 4, 0.10f, PlayMode.ONCE))
            .add("AttackRight", sheet.createAnimation("AttackRight", 7, 0, 4, 0.10f, PlayMode.ONCE))
            .add("AttackUp",    sheet.createAnimation("AttackUp",    8, 0, 4, 0.10f, PlayMode.ONCE))
            .add("Die",         sheet.createAnimation("Die",         9, 0, 3, 0.20f, PlayMode.ONCE));
        anim.play("IdleRight");

        var sprite = new Sprite2DComponent();
        sprite.scale = 8f;

        var transform = new Transform2DComponent();
        transform.position.set(960, 540);

        var player = world.entities.createEntity();
        world.entities.addComponent(player, transform);
        world.entities.addComponent(player, sprite);
        world.entities.addComponent(player, anim);
        world.entities.addComponent(player, new PlayerStateComponent(300f));

        world.addSystem(new PlayerControlSystem(world.entities));

        getCamera().position.set(960, 540);

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

---

## Step 10 — Delete old classes

| File | Replaced by |
|------|-------------|
| `gameobject/Component.java` | `ecs/Component.java` (marker interface) |
| `gameobject/GameObject.java` | `EntityManager` + `Entity` |
| `gameobject/Transform2D.java` | `Transform2DComponent` |
| `gameobject/GameScene2D.java` | `ECSScene2D` |
| `gameobject/components/SpriteRenderer2D.java` | `Sprite2DComponent` + `SpriteRender2DSystem` |
| `gameobject/components/Animator2D.java` | `Animation2DComponent` + `Animation2DSystem` |
| `sandbox/PlayerController.java` | `PlayerControlSystem` + `PlayerStateComponent` |

---

## Step 11 — Update `module-info.java`

```java
// Remove:
exports hu.mudlee.core.gameobject;
exports hu.mudlee.core.gameobject.components;

// Add:
exports hu.mudlee.core.ecs;
exports hu.mudlee.core.ecs.component;
exports hu.mudlee.core.ecs.system;
```

---

## Step 12 — Format, build, verify

```bash
./gradlew spotlessApply
./gradlew build
./gradlew run
```

Expected: sandbox window opens, player sprite at centre, arrow keys move, space attacks,
X triggers die animation — identical runtime behaviour to before the migration.

---

## Execution Order

| Step                                                    | Depends on           |
|---------------------------------------------------------|----------------------|
| 1 — `Entity`                                            | —                    |
| 2 — `Component` interface                               | —                    |
| 3 — `EntityManager`                                     | 1, 2                 |
| 4 — `SystemBase` / `RenderSystemBase` / `SystemManager` | 3                    |
| 5 — `World`                                             | 4                    |
| 6 — data components                                     | 2                    |
| 7 — `Animation2DSystem`, `SpriteRender2DSystem`         | 4, 6                 |
| 8 — `ECSScene2D`                                        | 5, 6, 7              |
| 9 — sandbox rewrite                                     | 8                    |
| 10 — delete old classes                                 | 9 (verified working) |
| 11 — `module-info.java`                                 | 10                   |
| 12 — format + build + verify                            | 11                   |

Steps 1 and 2 are independent. Steps 6 and 7 are independent once step 4 is done.

---

## Post-Migration Improvements (Step 13+)

- **Query key interning**: replace per-call `HashSet` allocation with a pre-registered
  `int` signature ID. Zero allocation on `getEntitiesWith`.
- **Archetype tables**: group entities by exact component-set. O(1) system iteration.
- **Array-based storage**: replace `HashMap<Integer, Component>` with `Component[]` indexed
  by dense entity IDs. Eliminates boxing, improves cache locality.
- **Parallel systems**: run independent update systems on `ForkJoinPool.commonPool()`.
