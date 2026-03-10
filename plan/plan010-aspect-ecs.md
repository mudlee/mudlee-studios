# Plan 010 — Aspect-Based ECS (MonoGame Extended Style)

## Goal

Eliminate `EntityManager` from system constructors. Systems declare their component
requirements via `Aspect`, receive typed `ComponentMapper<T>` instances through an
`initialize()` hook, and iterate entities without ever referencing `EntityManager` directly.

Reference: MonoGame Extended `EntityProcessingSystem` / `IComponentMapperService`.

---

## Pattern Being Adopted

```csharp
// MonoGame Extended
public class Animation2DSystem() : EntityProcessingSystem(Aspect.All(typeof(Animation2DComponent)))
{
    private ComponentMapper<Animation2DComponent> _animMapper = null!;

    public override void Initialize(IComponentMapperService mapperService) =>
        _animMapper = mapperService.GetMapper<Animation2DComponent>();

    public override void Process(GameTime gameTime, int entityId) =>
        _animMapper.Get(entityId).Animator.Update(gameTime);
}
```

Java target:

```java
public final class Animation2DSystem extends EntityProcessingSystem {

    private ComponentMapper<Animation2DComponent> animMapper;
    private ComponentMapper<Sprite2DComponent> spriteMapper;

    public Animation2DSystem() {
        super(Aspect.all(Animation2DComponent.class, Sprite2DComponent.class));
    }

    @Override
    public void initialize(ComponentMapperService mappers) {
        animMapper  = mappers.getMapper(Animation2DComponent.class);
        spriteMapper = mappers.getMapper(Sprite2DComponent.class);
    }

    @Override
    protected void process(GameTime gameTime, Entity entity) {
        var anim   = animMapper.get(entity);
        var sprite = spriteMapper.get(entity);
        anim.player.update(gameTime);
        sprite.region = anim.player.getCurrentFrame();
    }
}
```

---

## New Classes

### 1. `Aspect` — component filter declaration

**File:** `core/.../ecs/Aspect.java`

```java
public final class Aspect {

    final Class<? extends Component>[] all;

    @SafeVarargs
    private Aspect(Class<? extends Component>... all) {
        this.all = all;
    }

    @SafeVarargs
    public static Aspect all(Class<? extends Component>... types) {
        return new Aspect(types);
    }
}
```

Simple value object. No logic — `EntityProcessingSystem` passes `aspect.all` straight to
`em.getEntitiesWith()`.

---

### 2. `ComponentMapper<T>` — O(1) typed component accessor

**File:** `core/.../ecs/ComponentMapper.java`

```java
public final class ComponentMapper<T extends Component> {

    private final EntityManager em;
    private final Class<T> type;

    ComponentMapper(EntityManager em, Class<T> type) {
        this.em = em;
        this.type = type;
    }

    public T get(Entity entity) {
        return em.getComponent(entity, type);
    }

    public boolean has(Entity entity) {
        return em.hasComponent(entity, type);
    }
}
```

Package-private constructor — only `ComponentMapperService` creates instances.
`get()` delegates to `EntityManager.getComponent()`, which already throws on missing.

---

### 3. `ComponentMapperService` — mapper factory injected into systems

**File:** `core/.../ecs/ComponentMapperService.java`

```java
public final class ComponentMapperService {

    private final EntityManager em;

    ComponentMapperService(EntityManager em) {
        this.em = em;
    }

    public <T extends Component> ComponentMapper<T> getMapper(Class<T> type) {
        return new ComponentMapper<>(em, type);
    }
}
```

Package-private constructor — only `World` instantiates it. Not an interface: there is
only one implementation and no test-mocking need (the mapper itself is trivially thin).

---

### 4. `EntityProcessingSystem` — new base for per-entity update systems

**File:** `core/.../ecs/EntityProcessingSystem.java`

```java
public abstract class EntityProcessingSystem extends SystemBase {

    private final Aspect aspect;

    protected EntityProcessingSystem(Aspect aspect) {
        this.aspect = aspect;
    }

    public void initialize(ComponentMapperService mappers) {}

    @Override
    public final void update(GameTime gameTime) {
        var entities = em.getEntitiesWith(aspect.all);
        for (int i = 0; i < entities.size(); i++) {
            process(gameTime, entities.get(i));
        }
    }

    protected abstract void process(GameTime gameTime, Entity entity);
}
```

`initialize()` is non-abstract with a no-op default so systems that need no mappers
(rare, but possible) skip the boilerplate entirely.

---

## Modified Classes

### `SystemBase` — add no-arg constructor and package-private injection

The EM constructor remains for backward compatibility. Add:

```java
// package-private, used only by EntityProcessingSystem path
SystemBase() {}

// called by World before onStart()
void injectEntityManager(EntityManager em) {
    this.em = em;
}
```

Change `em` field from `final` to non-final. Existing systems that pass `em` in their
constructor are unaffected.

---

### `World.addSystem()` — wire up injection and initialization

```java
public void addSystem(SystemBase system) {
    if (system instanceof EntityProcessingSystem eps) {
        eps.injectEntityManager(entities);
        eps.initialize(new ComponentMapperService(entities));
    }
    system.onStart();
    systems.add(system);
}
```

`RenderSystemBase` subclasses that still take `em` in constructor continue to work
unchanged — the `instanceof` check only triggers for the new path.

---

## Render Systems

`RenderSystemBase` systems (e.g., `SpriteRender2DSystem`) perform batch rendering, not
per-entity callbacks — a `process(entity)` loop would not fit their sort-then-draw
pattern. They are **not** migrated to `EntityProcessingSystem`.

They do benefit from `ComponentMapper` for the lookup inside their `render()` loop.
Migration: remove `EntityManager` from their constructor, use the `initialize()` hook
(add `initialize(ComponentMapperService)` to `RenderSystemBase` as well), and store
mappers as fields.

---

## Migration of Existing Systems

| System | Action |
|---|---|
| `Animation2DSystem` | Extend `EntityProcessingSystem`, use mappers |
| `PlayerControlSystem` (sandbox) | Extend `EntityProcessingSystem`, use mappers |
| `SpriteRender2DSystem` | Keep `RenderSystemBase`, add mapper injection via `initialize()` |

---

## What Does NOT Change

- `EntityManager` API — untouched
- `Aspect` has no exclude/optional support for now — add later if needed
- `SystemManager` — untouched
- Existing systems that still pass `em` directly continue to compile and work

---

## File Checklist

```
NEW  core/.../ecs/Aspect.java
NEW  core/.../ecs/ComponentMapper.java
NEW  core/.../ecs/ComponentMapperService.java
NEW  core/.../ecs/EntityProcessingSystem.java
MOD  core/.../ecs/SystemBase.java          (no-arg ctor + injectEntityManager)
MOD  core/.../ecs/RenderSystemBase.java    (no-arg ctor + initialize hook)
MOD  core/.../ecs/World.java               (injection + initialize call in addSystem)
MOD  core/.../ecs/system/Animation2DSystem.java
MOD  core/.../ecs/system/SpriteRender2DSystem.java
MOD  sandbox/.../PlayerControlSystem.java
```
