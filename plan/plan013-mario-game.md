# Plan 013: Classic Mario Game (Two Screens)

## Overview

Create a simplified recreation of the first screen of Super Mario Bros. When Mario reaches the second screen, he wins. Features include: movement, jumping, hitting blocks for coins, coin counter HUD, Goombas that can kill Mario, game over screen, and win screen.

## Engine Capability Assessment

### ✅ What the Engine Already Supports

| Feature | Engine Support | Notes |
|---------|----------------|-------|
| **2D Sprite Rendering** | ✅ Excellent | SpriteBatch2D with batching, Z-ordering |
| **Sprite Sheets** | ✅ Excellent | SpriteSheet2D, TextureAtlas with auto-packing |
| **Animations** | ✅ Excellent | Animation2DSystem, LOOP/ONCE/PINGPONG modes |
| **Sprite Flipping** | ✅ Excellent | flipX/flipY for left/right facing Mario |
| **Keyboard Input** | ✅ Excellent | Full key enum, per-frame state |
| **Gamepad Input** | ✅ Excellent | Xbox/PS4 compatible via GLFW |
| **Action-Based Input** | ✅ Excellent | Unity-style InputAction/InputActionMap |
| **Screen Management** | ✅ Excellent | Stack-based, push/pop/set transitions |
| **Camera 2D** | ✅ Excellent | Position/zoom, can follow player |
| **Text Rendering** | ✅ Excellent | BitmapFont with TrueType, UIBatch |
| **Content Loading** | ✅ Good | ContentManager with caching |
| **ECS Architecture** | ✅ Good | Entity/Component/System pattern |
| **Transform 2D** | ✅ Good | Position, rotation, scale, Z-order |

### ❌ What the Engine is Missing (Must Be Added)

| Feature | Priority | Complexity | Technology Decision |
|---------|----------|------------|---------------------|
| **Collision Detection** | 🔴 Critical | Medium | Custom AABB — no library needed |
| **Physics/Gravity** | 🔴 Critical | Medium | Custom kinematic — no library (dyn4j noted for future) |
| **Audio System** | 🔴 Critical | Medium-High | Pure LWJGL: OpenAL + STBVorbis — zero new dependencies |
| **Tilemap Rendering** | 🟡 High | Medium | Custom implementation — no Java TMX library is well-maintained |
| **Camera Bounds** | 🟡 High | Low | ~50 lines in OrthographicCamera2D |

---

## Required Engine Additions

### 1. Collision System

**What's Needed:**
- `BoundingBox2DComponent` — AABB collider (width, height, offset from transform)
- `CollisionLayer` enum — PLAYER, ENEMY, BLOCK, COIN, PLATFORM, TRIGGER
- `CollisionSystem` — Detects overlaps each frame, generates collision events
- `CollisionEvent` — Contains: entityA, entityB, overlap direction, overlap amount

**Key Collision Pairs for Mario:**
- Mario ↔ Platform → Stop falling, allow walking
- Mario ↔ Block (from below) → Trigger block hit, spawn coin
- Mario ↔ Coin → Collect coin, increase score
- Mario ↔ Goomba (from above) → Kill Goomba
- Mario ↔ Goomba (from side) → Kill Mario
- Mario ↔ Level End Trigger → Win game

**Implementation Notes:**
- Simple AABB is sufficient (no rotation needed)
- Spatial partitioning (grid or quadtree) optional for two screens
- Collision response handled by game-specific systems, not engine

### 2. Physics System

**Decision: Custom implementation (no library)**

A Mario platformer uses simple kinematic physics — gravity + velocity integration with AABB resolution. No rigid body simulation, no joints, no rotation. A dedicated physics library would add significant API surface overhead for what is essentially three lines of math per frame.

**What's Needed:**
- `RigidBody2DComponent` — velocity (vx, vy), gravity scale, grounded flag, kinematic flag
- `PhysicsSystem` — Applies gravity, integrates velocity, updates Transform position
- Ground detection via downward collision check after `CollisionSystem` runs

**Physics Values (typical Mario feel):**
- Gravity: ~1500 pixels/sec²
- Jump velocity: ~600 pixels/sec (initial upward)
- Walk speed: ~200 pixels/sec
- Max fall speed: ~800 pixels/sec (terminal velocity)
- Ground friction: zero (Mario has no deceleration, only the player input stops him)

**Implementation Notes:**
- Fixed timestep recommended for deterministic physics (`accumulator` pattern)
- Collision resolution: move entity out of penetration depth, zero velocity in collision normal axis
- Kinematic flag for entities that move but are not affected by gravity (Goombas have their own walk logic)

> **Why not dyn4j?** dyn4j (v5.0.2, zero dependencies, actively maintained, Maven Central) is the best Java 2D physics library if you need realistic simulation. For this project, the overhead of mapping dyn4j bodies ↔ ECS components and keeping them in sync every frame adds complexity without any benefit. Revisit dyn4j if the engine needs realistic rigid-body simulation in the future.

### 3. Audio System

**Decision: Pure LWJGL — OpenAL + STBVorbis (no new dependencies)**

LWJGL already ships OpenAL bindings and STBVorbis. This is the standard approach for Java game audio in 2024 and requires zero additional dependencies.

- **Sound effects (.wav / .ogg):** Load entire file into a single OpenAL buffer, play from a pooled source. STBVorbis decodes OGG to raw PCM; standard Java `AudioInputStream` handles WAV.
- **Music streaming (.ogg):** Double/triple OpenAL buffer queue filled by STBVorbis on a dedicated background thread. As each buffer is consumed, it is unqueued, refilled with the next decoded chunk, and re-queued. Buffer size ~16 KB per buffer, 3 buffers → smooth playback with minimal latency.

**What's Needed:**
- `AudioDevice` — `alcCreateContext`, device open/close, holds global volume state
- `SoundEffect` — Wraps a pre-loaded OpenAL buffer. `play(volume, pitch)` grabs a free source from a pool (pool size ~16 sources).
- `Music` — Owns a streaming decoder thread, 3-buffer queue, play/pause/stop/loop API
- `AudioManager` (GameService) — Owns `AudioDevice`, exposes `playSfx()` / `playMusic()`, master + category volumes

**Sounds Needed for Mario:**
- Jump, coin collect, block hit, Goomba stomp, death (SFX — short, loaded fully)
- Level theme, game over jingle, win jingle (Music — streamed)

**Implementation Notes:**
- Pool OpenAL sources for SFX: never allocate per-play, avoids GC during gameplay
- Streaming thread uses `MemoryUtil.memAlloc` / `memFree` for PCM scratch buffer — no heap allocation
- Call `alSourceStop` + `alDeleteSources` on all sources during cleanup to prevent AL leaks

### 4. Tilemap System

**Decision: Custom lightweight implementation (no library)**

Available Java TMX-loading libraries are either tightly coupled to LibGDX (too heavy — pulls in the whole LibGDX runtime), poorly maintained, or not available on Maven Central. The only credible pure-Java option in 2024 is writing it yourself.

For a two-screen Mario level this is entirely justified:

**What's Needed:**
- `Tileset` — Wraps a `Texture2D`, maps tile IDs to UV regions (tile size, columns, rows)
- `TilemapLayer` — 2D `int[][]` of tile IDs; `-1` = empty
- `Tilemap` — Holds one or more `TilemapLayer`s (e.g. background + foreground), tile size, world dimensions
- `TilemapRenderer` — Iterates visible tiles (camera frustum cull), issues one `SpriteBatch2D` draw call per tile. Uses the existing `SpriteBatch2D` so no new GL state needed.
- `TilemapCollider` — Marks tiles as solid (by ID or a boolean layer). Generates `BoundingBox2DComponent` entities or a flat list of AABBs for the collision system to test against.

**Level data format:** Plain Java 2D int arrays hardcoded in the level class for two screens — no file parser needed. If the engine grows, JSON export from Tiled can be read with the standard `javax.json` or a minimal GSON dependency.

**Tile rendering tip:** Only iterate the tile range that falls within the camera viewport each frame. For a 64×14 map this is negligible, but the pattern scales.

### 5. Camera Bounds

**What's Needed:**
- `CameraBounds` — min/max X/Y the camera can move to
- Update `OrthographicCamera2D` to clamp position within bounds

**Simple Implementation:**
```
camera.x = clamp(camera.x, bounds.minX + halfWidth, bounds.maxX - halfWidth)
camera.y = clamp(camera.y, bounds.minY + halfHeight, bounds.maxY - halfHeight)
```

---

## Game-Specific Systems (Not Engine, Built for Mario)

These systems use the engine features but are specific to the Mario game:

1. **PlayerControllerSystem** — Reads input, sets player velocity, handles jump input
2. **PlayerAnimationSystem** — Switches between idle/walk/jump animations based on state
3. **GoombaAISystem** — Moves Goombas left/right, reverses at edges/walls
4. **BlockHitSystem** — Detects Mario hitting block from below, spawns coin
5. **CoinCollectionSystem** — Handles coin pickup, updates score
6. **PlayerDeathSystem** — Detects Goomba collision, triggers death sequence
7. **CameraFollowSystem** — Makes camera follow Mario horizontally
8. **LevelEndSystem** — Detects Mario reaching end trigger, shows win screen

---

## Screen Structure

```
┌─────────────────────────────────────────────────────────────┐
│                        Game Flow                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌──────────┐     ┌───────────┐     ┌─────────────┐        │
│   │  Title   │────▶│  Gameplay │────▶│  Win Screen │        │
│   │  Screen  │     │  Screen   │     │             │        │
│   └──────────┘     └─────┬─────┘     └─────────────┘        │
│                          │                                   │
│                          ▼                                   │
│                    ┌───────────┐                             │
│                    │ Game Over │                             │
│                    │  Screen   │                             │
│                    └───────────┘                             │
│                          │                                   │
│                          ▼                                   │
│                    Restart or Title                          │
└─────────────────────────────────────────────────────────────┘
```

**Screens to Implement:**
1. **TitleScreen** — Press Start to begin
2. **GameplayScreen** — Main game with Mario, uses ScreenManager.set()
3. **GameOverScreen** — "GAME OVER", press to restart
4. **WinScreen** — Congratulations, shows coin count

---

## HUD Layout

```
┌────────────────────────────────────────────────────────────────┐
│  MARIO                WORLD              TIME                  │
│  000000               1-1                 400                  │
│  🪙 × 00                                                       │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│                     (Game World Here)                          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**HUD Elements:**
- Coin icon + count (top-left area)
- Optional: World indicator, Timer (for authenticity)

---

## Level Design (Two Screens)

**Screen 1:**
- Ground platform
- 3-4 question blocks with coins
- 1-2 brick blocks
- 2 Goombas patrolling
- Some pipes (visual only)

**Screen 2:**
- Ground platform continues
- 1-2 more blocks
- 1 Goomba
- End flag/trigger at right edge

**Level Width:** ~32 tiles × 2 screens = ~1024 pixels wide (at 16px tiles)
**Level Height:** ~14 tiles = ~224 pixels (classic NES resolution scaled)

---

## Asset Requirements

### Sprites (create or source)
- Mario: idle, walk (2-3 frames), jump
- Goomba: walk (2 frames), squashed
- Question block: animated shine (3-4 frames), hit (static)
- Brick block: static
- Ground tiles
- Pipe tiles
- Coin: spinning (4 frames)
- Flag/end marker

### Audio (user has these)
- SFX: jump, coin, block hit, stomp, death
- Music: level theme, game over, win jingle

### Fonts
- NES-style pixel font for HUD

---

## Implementation Order

### Phase 1: Engine Core Additions
1. Collision system (BoundingBox2D, CollisionSystem, collision events)
2. Physics system (RigidBody2D, gravity, velocity integration)
3. Audio system (AudioDevice, SoundEffect pool, Music streaming via STBVorbis)
4. Tilemap system (Tileset, TilemapLayer, TilemapRenderer, TilemapCollider)
5. Camera bounds

### Phase 2: Game Foundation
5. Create sprite sheets for Mario, Goomba, tiles
6. Set up GameplayScreen with basic level layout
7. Implement PlayerControllerSystem (movement + jump)
8. Implement camera follow

### Phase 3: Core Mechanics
9. Platform collision (Mario lands on ground/blocks)
10. Block hit detection (Mario hits from below)
11. Coin spawning and collection
12. HUD with coin counter

### Phase 4: Enemies & Death
13. Goomba entity with walk AI
14. Goomba collision (stomp vs side hit)
15. Player death sequence
16. Game over screen

### Phase 5: Polish & Win
17. Win trigger at level end
18. Win screen
19. Sound effects integration
20. Background music
21. Title screen
22. Visual polish (animations, particles if desired)

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Collision detection edge cases | Medium | High | Extensive testing, use proven AABB algorithms |
| Physics feel "off" | Medium | Medium | Iterate on values, reference original Mario physics |
| Audio system complexity | Medium | Medium | Start simple (OpenAL basics), expand as needed |
| Performance with many entities | Low | Low | Only ~50-100 entities for two screens |
| Animation sync issues | Low | Medium | Use engine's Animation2DSystem, test transitions |

---

## Estimated Scope

**Engine Additions:** ~1500-2500 lines of code
- Collision system: ~500 lines
- Physics system: ~300 lines  
- Audio system: ~700-1000 lines
- Camera bounds: ~50 lines

**Game Code:** ~1500-2000 lines of code
- Screens: ~400 lines
- Player systems: ~400 lines
- Enemy systems: ~200 lines
- Level/block systems: ~300 lines
- HUD: ~200 lines

**Total:** ~3000-4500 lines of new code

---

## Summary

The engine has **excellent 2D rendering, animation, input, and screen management** — all critical foundations for a Mario game. However, it is **missing collision detection, physics, audio, and tilemap rendering** which are essential for gameplay.

**Technology Decisions:**

| Feature | Approach | New Dependency |
|---------|----------|----------------|
| Collision | Custom AABB system | None |
| Physics | Custom kinematic integration | None (`dyn4j` noted for future rigid-body needs) |
| Audio | LWJGL OpenAL + STBVorbis streaming | None (already in LWJGL) |
| Tilemap | Custom `Tilemap`/`TilemapRenderer` | None |
| Camera Bounds | Extend `OrthographicCamera2D` | None |

**Verdict:** The engine requires four additions before the Mario game can be built — all implemented with zero new library dependencies, using only existing LWJGL capabilities and custom code:
1. ✅ Add collision detection system (AABB-based)
2. ✅ Add physics system (custom kinematic gravity + velocity)
3. ✅ Add audio system (OpenAL sources + STBVorbis OGG streaming)
4. ✅ Add tilemap system (custom int-array tiles + SpriteBatch2D rendering)

With these additions, the engine will be fully capable of supporting this classic Mario recreation and future 2D platformer games.
