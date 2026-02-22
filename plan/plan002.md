# Plan 002 — Sprite Sheet Animation

## Objective

Load a player sprite sheet that contains multiple animations (idle, run) alongside embedded
text labels, extract individual frames, define named animations, and drive them in real time
via a stateful player. The end result is a playable demo where the player idles by default
and transitions to the run animation on key input.

## Reference

- LibGDX TextureRegion / Animation API: https://libgdx.com/wiki/graphics/2d/sprite-batch-textureregion-and-sprite
- LibGDX Animation: https://libgdx.com/wiki/graphics/2d/2d-animation
- MonoGame SpriteSheet sample: https://github.com/MonoGame/MonoGame.Samples

---

## The Problem: Sprite Sheets with Embedded Text

Many free sprite assets embed human-readable text labels directly on the image (animation
names, frame numbers, copyright notices). This makes automatic frame detection unreliable
because pixel rows containing text cannot be treated as animation frames.

The solution used throughout this plan is **manual measurement + explicit configuration**:

1. Open the sprite in an image editor (GIMP, Photoshop, Paint.NET).
2. Measure and record:
   - Total image dimensions (already available via `Texture2D.getWidth()` / `getHeight()`)
   - Frame size: width × height of a single animation frame in pixels
   - Margin: gap between the image edge and the first frame (top-left origin)
   - Spacing: gap between adjacent frames (horizontal and vertical)
   - Animation rows: which row index holds each animation, and how many frames it has
3. Encode these measurements as a `SpriteSheet` configuration in code.

This is a one-time, per-asset task. No runtime image analysis is needed.

---

## What the Target API Looks Like (Goal)

```java
@Override
public void show() {
    var texture = content.load(Texture2D.class, "player");

    var sheet = new SpriteSheet(texture, 96, 84)  // frameWidth, frameHeight
            .withMargin(8, 8)                      // marginX, marginY in pixels
            .withSpacing(0, 0);                    // spacingX, spacingY in pixels

    var idle = sheet.createAnimation("Idle", 0, 0, 4, 0.15f, PlayMode.LOOP);
    var run  = sheet.createAnimation("Run",  1, 0, 8, 0.1f,  PlayMode.LOOP);

    player = new AnimationPlayer();
    player.play(idle);
}

@Override
public void update(GameTime gameTime) {
    var ks = Keyboard.getState();
    player.play(ks.isKeyDown(Keys.RIGHT) ? run : idle);
    player.update(gameTime);
}

@Override
public void draw(GameTime gameTime) {
    graphicsDevice.clear(Color.BLACK);
    spriteBatch.begin(camera.getTransformMatrix());
    spriteBatch.draw(player.getCurrentFrame(), position, Color.WHITE);
    spriteBatch.end();
}
```

---

## Transformation Steps

### Step 1 — Measure the Sprite Sheet

Note: I completed it, now the table contains the right values.

| Property                      | Value    |
|-------------------------------|----------|
| Frame size                    | 48×48    |
| Margin (x, y)                 | 0, 0     |
| Spacing (x, y)                | 0, 0     |
| Row 0 — Idle facing ahead     | 6 frames |
| Row 1 — Idle facing right     | 6 frames |
| Row 2 — Idle facing backwards | 6 frames |
| Row 3 — Walking ahead         | 6 frames |
| Row 4 — Walking right         | 6 frames |
| Row 5 — Walking backwards     | 6 frames |
| Row 6 — Attacking ahead       | 4 frames |
| Row 7 — Attacking right       | 4 frames |
| Row 8 — Attacking backwards   | 4 frames |
| Row 9 — Die                   | 3 frames |

---

### Step 2 — Introduce `TextureRegion`

**What:** An immutable value type that pairs a `Texture2D` with a sub-rectangle of it in
pixel coordinates. All animation frames are `TextureRegion` instances.

**New class:** `hu.mudlee.core.render.texture.TextureRegion`

```java
public final class TextureRegion {
    public final Texture2D texture;
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public TextureRegion(Texture2D texture, int x, int y, int width, int height);

    // Pre-computed UV coordinates (0.0–1.0), used by SpriteBatch internally
    public float u0();
    public float v0();
    public float u1();
    public float v1();
}
```

UV coordinates are computed once at construction from the texture dimensions
(`texture.getWidth()` / `texture.getHeight()`), so the render loop never divides.

**Why:** `SpriteBatch` already supports a `sourceRect` parameter on its draw overloads. A
`TextureRegion` bundles the texture + source rect together so call sites are cleaner and the
UV math is done once.

---

### Step 3 — Introduce `SpriteSheet`

**What:** A builder-style utility that turns a `Texture2D` and layout parameters into a
grid of `TextureRegion` frames. Does **not** store or cache regions — it computes them on
demand.

**New class:** `hu.mudlee.core.render.texture.SpriteSheet`

```java
public final class SpriteSheet {
    public SpriteSheet(Texture2D texture, int frameWidth, int frameHeight);
    public SpriteSheet withMargin(int x, int y);    // pixel offset to first frame
    public SpriteSheet withSpacing(int x, int y);   // gap between frames

    // Returns the TextureRegion for the frame at column col, row row (0-based)
    public TextureRegion getRegion(int col, int row);

    // Convenience: extracts a contiguous run of frames from one row as an Animation
    public Animation createAnimation(String name, int row, int startCol,
                                     int frameCount, float frameDuration, PlayMode mode);
}
```

Column and row indices are 0-based and skip the margin and spacing automatically.

**Why:** Encapsulates the arithmetic of `x = marginX + col * (frameWidth + spacingX)`, etc.
Game code never deals with raw pixel offsets.

---

### Step 4 — Introduce `PlayMode` and `Animation`

**What:** A stateless sequence of `TextureRegion` frames with a playback mode. Stateless
means the same `Animation` instance can be shared by multiple players simultaneously.

**New enum:** `hu.mudlee.core.render.animation.PlayMode`

```java
public enum PlayMode {
    ONCE,           // plays through once, holds on last frame
    LOOP,           // loops forever
    LOOP_PINGPONG   // plays forward then backward, loops
}
```

**New class:** `hu.mudlee.core.render.animation.Animation`

```java
public final class Animation {
    public final String name;
    public final PlayMode playMode;
    public final float frameDuration; // seconds per frame

    public Animation(String name, TextureRegion[] frames,
                     float frameDuration, PlayMode playMode);

    // Returns the correct frame for the given accumulated playback time
    public TextureRegion getKeyFrame(float stateTime);

    // True only for ONCE mode after all frames have elapsed
    public boolean isFinished(float stateTime);

    public int getFrameCount();
    public float getTotalDuration(); // frameDuration * frameCount
}
```

`getKeyFrame` contains all the index arithmetic for each `PlayMode` so that logic never
leaks into game code.

**Why:** Keeping `Animation` stateless allows a single `idle` or `run` instance to be
safely referenced from multiple entities.

---

### Step 5 — Introduce `AnimationPlayer`

**What:** A stateful wrapper around `Animation` that tracks elapsed time and exposes the
current frame. This is the object game code holds per entity/character.

**New class:** `hu.mudlee.core.render.animation.AnimationPlayer`

```java
public final class AnimationPlayer {
    // Switches to the given animation, resets state time only if it is a different animation
    public void play(Animation animation);

    // Advances the internal state time by gameTime.elapsedSeconds()
    public void update(GameTime gameTime);

    // Returns the TextureRegion to render this frame
    public TextureRegion getCurrentFrame();

    // True when a ONCE animation has played through completely
    public boolean isFinished();

    // Resets state time to 0 without changing the animation
    public void reset();
}
```

Calling `play(idle)` every frame while idle does **not** reset the animation — the player
detects that it is already playing the same animation and ignores the call. This makes it
safe to write `player.play(isMoving ? run : idle)` unconditionally in `update()`.

**Why:** LibGDX makes game code track `stateTime` manually. An `AnimationPlayer` removes
that boilerplate and makes the idle→run transition trivial.

---

### Step 6 — Extend `SpriteBatch` with `TextureRegion` draw overloads

**What:** Add `draw` overloads that accept `TextureRegion` directly, so game code never
manually unpacks the source rectangle. Also complete the existing rotation/flip/scale
overload that currently ignores those parameters.

**New overloads on `SpriteBatch`:**

```java
// Basic positional draw
public void draw(TextureRegion region, Vector2f position, Color color);

// Full-featured: rotation (radians), origin, uniform scale, flip
public void draw(TextureRegion region, Vector2f position, Color color,
                 float rotation, Vector2f origin, float scale,
                 boolean flipX, boolean flipY);
```

The existing `Texture2D`-based overloads remain unchanged for backwards compatibility.
Internally the new overloads simply delegate to the existing private `draw(...)` method
after extracting UVs from the region.

**Also:** Implement the `rotation`, `origin`, `flipX`, `flipY` parameters that were
stubbed out in the original `Texture2D` overload. The vertex transform is:

```
origin-relative rotation → apply flip → translate to position
```

This is applied per-quad on the CPU before uploading to the vertex buffer.

**Why:** Once `AnimationPlayer.getCurrentFrame()` returns a `TextureRegion`, the draw call
should be one line with no unpacking.

---

### Step 7 — Demo: Player Idle and Run in Sandbox

**What:** Wire everything together in a new `PlayerScreen` in the sandbox. Replace
`GameScreen` as the initial screen, or push `PlayerScreen` as a second screen to show
`ScreenManager` navigating between screens.

**Behaviour:**
- KnigPlayerht idles when no key is held.
- Player runs (and flips horizontally) when `LEFT` or `RIGHT` arrow is held.
- Camera is centred on the Player.

**Skeleton:**

```java
public class PlayerScreen implements Screen {

    private AnimationPlayer player;
    private Animation idle;
    private Animation run;
    private Vector2f position;

    @Override
    public void show() {
        var texture = content.load(Texture2D.class, "player");
        var sheet = new SpriteSheet(texture, FRAME_W, FRAME_H)
                .withMargin(MARGIN_X, MARGIN_Y);

        idle = sheet.createAnimation("Idle", ROW_IDLE, 0, IDLE_FRAMES, 0.15f, PlayMode.LOOP);
        run  = sheet.createAnimation("Run",  ROW_RUN,  0, RUN_FRAMES,  0.1f,  PlayMode.LOOP);
       //... etc the rest based on the table and wire animations for different keys to be able to test

        player = new AnimationPlayer();
        player.play(idle);
        position = new Vector2f(960, 540);
    }

    @Override
    public void update(GameTime gameTime) {
        var ks = Keyboard.getState();
        boolean moving = ks.isKeyDown(Keys.LEFT) || ks.isKeyDown(Keys.RIGHT);
        player.play(moving ? run : idle);
        player.update(gameTime);
        if (ks.isKeyDown(Keys.LEFT))  { position.x -= SPEED * gameTime.elapsedSeconds(); }
        if (ks.isKeyDown(Keys.RIGHT)) { position.x += SPEED * gameTime.elapsedSeconds(); }
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        spriteBatch.begin(camera.getTransformMatrix());
        var ks = Keyboard.getState();
        spriteBatch.draw(player.getCurrentFrame(), position, Color.WHITE,
                0f, Vector2f.ZERO, 1f, ks.isKeyDown(Keys.LEFT), false);
        spriteBatch.end();
    }
}
```

The constants (`FRAME_W`, `FRAME_H`, `ROW_IDLE`, etc.) are filled in from the measurements
recorded in Step 1.

---

## Execution Order Summary

| Step | What | New Introduced |
|------|------|----------------|
| 1 | Measure the sprite sheet | Frame size, margin, spacing, row map (constants) |
| 2 | `TextureRegion` | Texture + pixel sub-rect, pre-computed UVs |
| 3 | `SpriteSheet` | Grid extractor, `createAnimation` factory |
| 4 | `PlayMode` + `Animation` | Stateless frame sequence, `getKeyFrame(stateTime)` |
| 5 | `AnimationPlayer` | Stateful player, `play(Animation)`, `update(GameTime)` |
| 6 | SpriteBatch `TextureRegion` overloads | Clean draw API, implement rotation/flip/scale |
| 7 | `KnightScreen` demo | Knight idle + run with horizontal flip |

Steps 1–3 must be done in order (each depends on the previous). Steps 4 and 5 can be done
in parallel with Step 3 once the `TextureRegion` type exists. Step 6 can be done in
parallel with Steps 4–5. Step 7 requires all prior steps.
