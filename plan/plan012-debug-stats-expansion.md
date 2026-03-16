# Plan 012 — Debug Stats Expansion + Warning System

## Goal

Expand `DebugStatsComponent` with more actionable stats and introduce a colour-coded warning
system. Instead of the developer having to judge numbers, the overlay prefixes values with
`[!]`, `[!!]`, or `[!!!]` in red text (with black border) when a stat crosses a known
industry threshold. No warning prefix is shown when everything is healthy.

---

## New Stats to Add

The following stats are added on top of the existing four (FPS, frame time, heap, draw calls).

| Stat                      | Source                                                                        | Format          |
|---------------------------|-------------------------------------------------------------------------------|-----------------|
| Scene                     | `ScreenManager.getActiveScreenName()`                                         | `string`        |
| Min / Max FPS             | Rolling 60-frame window (same as average FPS)                                 | `min / max`     |
| Heap allocation rate      | Snapshot of `totalMemory` delta between frames                                | `X.X MB/s`      |
| GC pause count            | `GarbageCollectorMXBean` (sum of collection counts delta)                     | `N pauses / s`  |
| Off-heap (direct) memory  | `BufferPoolMXBean` for `direct` pool                                          | `X MB`          |
| Batch flush count         | `SpriteBatch` exposes `getFlushCount()`, reset each frame                     | `N / frame`     |
| Vertex count              | `Renderer` exposes `getVertexCount()`, reset each frame                       | `N`             |
| Loaded texture count      | `Renderer.getTextureCount()`                                                  | `N`             |
| Window size + pixel ratio | `Window.getWidth()`, `getHeight()`, `getPixelRatio()`                         | `1920x1080 @2x` |
| GPU                       | `GraphicsContext.getRendererInfo()` — each backend returns its own string; read once at init | `string`        |
| Engine uptime             | `GameTime.getTotalSeconds()`                                                  | `HH:MM:SS`      |

**Excluded from this plan (lower value / high complexity):** ECS entity count (Ashley path is
legacy), VSync status (requires OS query), frame time sparkline (needs custom draw primitive
not yet in UIBatch).

---

## Warning System

### Levels and Prefixes

| Level      | Prefix        | Meaning                                     |
|------------|---------------|---------------------------------------------|
| `NONE`     | *(no prefix)* | Healthy — no action needed                  |
| `NOTICE`   | `[!]`         | Worth a look — slightly outside ideal range |
| `WARN`     | `[!!]`        | Getting bad — investigate soon              |
| `CRITICAL` | `[!!!]`       | Bad — actively hurts the player experience  |

### Visual Rules

- **NONE** — value rendered in `Color.WHITE` (current default).
- **NOTICE** — prefix + value in **yellow** (`Color.YELLOW`, or `#FFDD00`).
- **WARN** — prefix + value in **orange** (`#FF8800`).
- **CRITICAL** — prefix + value in **red** (`Color.RED`) with **black outline/shadow**.
  UIBatch already renders a 4-direction shadow for all text; for CRITICAL the shadow colour
  must be forced to `Color.BLACK` and the main colour to `Color.RED`, regardless of the
  current theme. All other levels keep the default single-pixel dark shadow.

The prefix and the value are rendered as one concatenated string (e.g. `[!!!] 24.3`), so
they share the same colour automatically.

### Thresholds (research-backed)

Sources: Unity/Unreal performance guidelines, JVM GC best practices (IBM, JVM Monitoring suites),
general FPS standards (60fps = modern target, 30fps = console minimum).

#### FPS (average)
| Range   | Level                                          |
|---------|------------------------------------------------|
| ≥ 55    | `NONE`                                         |
| 45 – 55 | `NOTICE` — below ideal PC target               |
| 30 – 44 | `WARN` — console-minimum territory             |
| < 30    | `CRITICAL` — universally considered unplayable |

#### Frame Time (ms)
Derived from the 60 fps budget (16.67 ms per frame).

| Range         | Level                                            |
|---------------|--------------------------------------------------|
| ≤ 18.67 ms    | `NONE`                                           |
| 16.67 – 22 ms | `NOTICE` — slightly over 60 fps budget (~45 fps) |
| 22 – 33 ms    | `WARN` — 30–45 fps territory                     |
| > 33 ms       | `CRITICAL` — below 30 fps equivalent             |

#### Heap Usage (%)
Based on IBM JVM heap sizing guidance and industry monitoring defaults.

| Range    | Level                                             |
|----------|---------------------------------------------------|
| < 70%    | `NONE`                                            |
| 70 – 80% | `NOTICE` — GC will start running more often       |
| 80 – 85% | `WARN` — high GC pressure risk                    |
| > 85%    | `CRITICAL` — OOM risk, severe GC thrashing likely |

#### Heap Allocation Rate (MB/s)
Based on JVM game-engine GC pressure heuristics (50–100 MB/s is the recommended alert range
for real-time systems).

| Range          | Level      |
|----------------|------------|
| < 50 MB/s      | `NONE`     |
| 50 – 100 MB/s  | `NOTICE`   |
| 100 – 200 MB/s | `WARN`     |
| > 200 MB/s     | `CRITICAL` |

#### GC Pause Count (pauses/second)
Derived from JVM monitoring best practices — any GC pause inside a frame budget is harmful.

| Range | Level      |
|-------|------------|
| 0     | `NONE`     |
| 1 – 2 | `NOTICE`   |
| 3 – 9 | `WARN`     |
| ≥ 10  | `CRITICAL` |

#### Off-Heap Direct Memory (MB)
No universal standard; threshold is set relative to the JVM's `-XX:MaxDirectMemorySize`
(defaults to the `-Xmx` value). Expressed as a percentage of max direct memory.

| Range    | Level      |
|----------|------------|
| < 50%    | `NONE`     |
| 50 – 70% | `NOTICE`   |
| 70 – 85% | `WARN`     |
| > 85%    | `CRITICAL` |

#### Draw Calls (per frame)
Based on Unity's 2D draw call recommendations and general GPU batching guidance.

| Range     | Level      |
|-----------|------------|
| < 100     | `NONE`     |
| 100 – 499 | `NOTICE`   |
| 500 – 999 | `WARN`     |
| ≥ 1000    | `CRITICAL` |

#### Batch Flush Count (per frame)
More flushes = worse batching = more draw calls. A SpriteBatch that never breaks should
flush once; realistic 2D scenes with mixed textures flush a handful of times.

| Range   | Level      |
|---------|------------|
| < 5     | `NONE`     |
| 5 – 14  | `NOTICE`   |
| 15 – 29 | `WARN`     |
| ≥ 30    | `CRITICAL` |

#### Vertex Count (per frame)
Rough 2D-engine guide. Values vary by scene, so this is intentionally conservative.

| Range           | Level      |
|-----------------|------------|
| < 10 000        | `NONE`     |
| 10 000 – 49 999 | `NOTICE`   |
| 50 000 – 99 999 | `WARN`     |
| ≥ 100 000       | `CRITICAL` |

Stats without meaningful universal thresholds (scene name, window size, GPU string,
uptime, texture count) display with `NONE` level always — purely informational.

---

## Architecture

### New Classes

#### `WarningLevel.java` (new — `hu.mudlee.core.ui`)

```java
public enum WarningLevel {
    NONE("", Color.WHITE, Color.WHITE),
    NOTICE("[!] ", Color.YELLOW, /* dark shadow */ new Color(0.2f, 0.2f, 0f, 1f)),
    WARN("[!!] ", new Color(1f, 0.53f, 0f, 1f), new Color(0.3f, 0.15f, 0f, 1f)),
    CRITICAL("[!!!] ", Color.RED, Color.BLACK);

    public final String prefix;
    public final Color color;
    public final Color shadowColor; // forced shadow, overrides UIBatch default
}
```

#### `StatThreshold.java` (new — `hu.mudlee.core.ui`)

A simple functional interface `evaluate(float value) -> WarningLevel`.

Each stat in `DebugStatsComponent` holds one `StatThreshold` instance (inline lambda), keeping
threshold logic co-located with the stat that owns it.

### Modified Classes

#### `DebugStatsComponent.java`

- Add fields for all new stats.
- Add one `StatThreshold` constant per warnable stat.
- In `draw(UIBatch)`: compute `WarningLevel` from the threshold, build the display string as
  `level.prefix + formattedValue`, then call `batch.drawText(font, str, x, y, level.color)`.
  For `CRITICAL`, call a second `batch.drawText` with `level.shadowColor` offset by 1 px in
  all four diagonal directions before drawing the main text (same technique already used for
  the default shadow, but with the override colour). Alternatively, expose a
  `drawTextWithShadow(font, str, x, y, textColor, shadowColor)` overload on `UIBatch`.
- Uptime is displayed in `HH:MM:SS` format, formatted once per second to avoid string
  allocation every frame.
- Static (never-changing) stats (GPU string via `GraphicsContext.getRendererInfo()`, window
  size, pixel ratio) are read once in `start()` / constructor and stored as strings — never
  queried in `update()`.

#### `Renderer.java`

- Add `private static int vertexCount` alongside `drawCallCount`.
- `incrementVertexCount(int n)` — called from `renderRaw`.
- `getVertexCount()` / `resetVertexCount()` — reset alongside `resetDrawCallCount()`.
- `getTextureCount()` — tracks how many texture instances are live (increment in the backend
  texture constructor, decrement in `dispose()`).
- `getRendererInfo()` — returns a backend-agnostic one-line string describing the GPU/driver
  (device name from `VkPhysicalDeviceProperties`). `GraphicsContext` interface gets this method.

#### `SpriteBatch` (or `SpriteBatch2D`)

- Add `private int flushCount` field.
- Increment in `flush()`.
- `getFlushCount()` accessor.
- Reset in `begin()`.

#### `UIBatch.java`

- Add overload: `drawText(BitmapFont, String, float x, float y, Color textColor, Color shadowColor)`.
  The current `drawText` uses a hard-coded shadow offset and colour; this overload allows the
  caller to specify both. `DebugStatsComponent` uses this for `CRITICAL` warnings.

#### `ScreenManager.java`

- Add `getActiveScreenName()` returning `screen.getClass().getSimpleName()` (no new interface
  needed, but a `Screen.getName()` default method returning the simple class name would be
  cleaner).

- `getPixelRatio()` already exists via `ScreenPixelRatioHandler` — just expose it if not
  already public. No new logic.

---

## Files to Create / Modify

| Action | File                                                                     |
|--------|--------------------------------------------------------------------------|
| Create | `core/.../ui/WarningLevel.java`                                          |
| Create | `core/.../ui/StatThreshold.java`                                         |
| Modify | `core/.../ui/DebugStatsComponent.java` — new stats + warning rendering   |
| Modify | `core/.../ui/UIBatch.java` — coloured shadow overload                    |
| Modify | `core/.../core/render/Renderer.java` — vertex count, texture count       |
| Modify | `core/.../core/render/SpriteBatch.java` (or SpriteBatch2D) — flush count |
| Modify | `core/.../core/ScreenManager.java` — `getActiveScreenName()`             |
| Modify | `core/.../core/Window.java` — expose pixel ratio if needed               |

---

## Layout Proposal

```
Scene        GameplayScene

FPS          [!!!] 24.3         Min/Max   18.1 / 61.4
Frame time   [!!!] 41.2 ms
Heap         [!!]  82% (410/512 MB)
Alloc rate   [!]   67 MB/s
GC pauses    2 / s
Off-heap     34 MB
Draw calls   [!]   143
Flushes      3 / frame
Vertices     8 204

Textures     12
Window       1920x1080 @2x
GPU          AMD Radeon RX 7900 (Vulkan)
Uptime       00:03:42
```

Left column is always white (label). Right column uses the warning colour when applicable.
The layout stays two-column (label / value) matching the current implementation.
