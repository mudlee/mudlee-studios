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

| Feature | Priority | Complexity | Notes |
|---------|----------|------------|-------|
| **Collision Detection** | 🔴 Critical | Medium | AABB-based collision for platforms, enemies, items, blocks |
| **Physics/Gravity** | 🔴 Critical | Medium | Gravity acceleration, velocity integration, ground detection |
| **Audio System** | 🔴 Critical | Medium-High | OpenAL integration for sound effects and music |
| **Tilemap Rendering** | 🟡 High | Medium | Efficient rendering of tile-based levels |
| **Camera Bounds** | 🟡 High | Low | Prevent camera from showing outside level |

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

**What's Needed:**
- `RigidBody2DComponent` — velocity, gravity scale, grounded flag, kinematic flag
- `PhysicsSystem` — Applies gravity, integrates velocity, updates position
- Ground detection via downward collision check

**Physics Values (typical Mario feel):**
- Gravity: ~1500 pixels/sec²
- Jump velocity: ~600 pixels/sec (initial upward)
- Walk speed: ~200 pixels/sec
- Max fall speed: ~800 pixels/sec (terminal velocity)

**Implementation Notes:**
- Fixed timestep recommended for deterministic physics
- Collision resolution: Move entity out of overlap, zero velocity in that direction

### 3. Audio System

**What's Needed:**
- `AudioDevice` — OpenAL context initialization
- `SoundEffect` — Short audio clips (.wav/.ogg), playable with pitch/volume
- `Music` — Streaming background audio, play/pause/stop/loop
- `AudioService` or `AudioManager` — Global volume, category volumes (SFX, Music)

**Sounds Needed for Mario:**
- Jump sound
- Coin collect sound
- Block hit sound
- Goomba stomp sound
- Death sound
- Win jingle
- Background music (level theme)
- Game over music

**Implementation Notes:**
- OpenAL is already available via LWJGL
- Consider pooling audio sources for concurrent sounds

### 4. Tilemap System (Optional but Recommended)

**What's Needed:**
- `Tilemap` — 2D array of tile IDs, tile size, layer support
- `Tileset` — Texture atlas mapped to tile IDs
- `TilemapRenderer` — Batch renders visible tiles efficiently
- `TilemapCollider` — Generates collision boxes from solid tiles

**Benefits:**
- Level design with tile-based tools (Tiled editor)
- Memory-efficient level storage
- Automatic collision from tile properties

**Alternative:**
- Place each block/platform as individual entities
- Works fine for two small screens but less scalable

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
3. Audio system (OpenAL, SoundEffect, Music)
4. Camera bounds

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

The engine has **excellent 2D rendering, animation, input, and screen management** — all critical foundations for a Mario game. However, it is **missing collision detection, physics, and audio** which are essential for gameplay.

**Verdict:** The engine requires three major additions before the Mario game can be built:
1. ✅ Add collision detection system (AABB-based)
2. ✅ Add physics system (gravity, velocity)
3. ✅ Add audio system (OpenAL for SFX and music)

With these additions, the engine will be fully capable of supporting this classic Mario recreation and future 2D platformer games.
