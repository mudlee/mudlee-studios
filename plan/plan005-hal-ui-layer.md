# Plan 005: HAL-Backed UI Layer (replacing Nuklear)

## Problem

The Nuklear-based UI is complex, brings heavy dependencies (Nuklear library, custom shaders,
immediate-mode windowing system), and is harder to reason about than necessary. The only actual
requirement is: **render text and simple 2D primitives as a screen-space overlay**, on top of any
scene — 2D today, 3D tomorrow.

## Answer: Will SpriteBatch2D survive?

**Yes.** SpriteBatch2D is a textured-quad batcher driven by an orthographic projection matrix. UI is
always screen-space quads regardless of whether the scene is 2D or 3D. When 3D is introduced:

- The 3D scene renders normally (perspective projection, depth test on)
- The UI layer renders after, in its own pass, with a screen-space ortho matrix and depth test disabled
- SpriteBatch2D is already backend-agnostic (it picks Vulkan shaders via `Shader.create()`)

Nothing about SpriteBatch2D needs to change for 3D. The UI layer just needs to own its own
SpriteBatch instance and render it last.

## Design

### New classes

**`BitmapFont`** (`core/render/font/`)

- Loads a `.ttf` file from resources via `stb_truetype` (already a dependency via `org.lwjgl.stb`)
- Bakes a glyph atlas bitmap at a chosen point size using `stbtt_BakeFontBitmap`
- Creates a `Texture2D` from the atlas pixels via `Texture2D.createFromPixels()` — fully HAL-backed
- Stores per-character metrics: UV rect, x/y offset, advance width
- Exposes: `BitmapFont(String ttfPath, float ptSize)`
- Lifetime managed by the owner (loaded once at startup, disposed on shutdown)

**`UIBatch`** (`core/ui/`)

- Owns a `SpriteBatch2D` and a screen-space orthographic matrix (pixel coords, y-down, origin top-left)
- `begin(int screenW, int screenH)` — sets up ortho matrix, disables depth test, enables blend, calls `spriteBatch.begin(...)`
- `end()` — calls `spriteBatch.end()`, re-enables depth test
- `drawText(BitmapFont font, String text, float x, float y, Color color)` — submits one quad per character into the SpriteBatch
- `drawSprite(Texture2D texture, float x, float y, float w, float h, Color tint)` — delegates to SpriteBatch
- `dispose()` — disposes the internal SpriteBatch

### Modified classes

**`UIComponent`**
- Change `draw(UIRenderer renderer)` → `draw(UIBatch batch)`

**`UICanvas`** / **`UIObject`**
- Same parameter type change cascades down

**`UIService`** (extends `GameService`)
- Remove `UIRenderer renderer` field; replace with `UIBatch uiBatch`
- Remove `initialize(long windowHandle)` — `UIBatch` needs no special init
- `draw(GameTime)` calls `uiBatch.begin(screenW, screenH)`, then `canvas.draw(uiBatch)`, then `uiBatch.end()`
- `resize(int w, int h)` stores new screen dimensions
- Constructor becomes `UIService()` (no renderer argument)

**`DebugStatsComponent`**
- Replace `renderer.beginWindow(...)` / `renderer.text(...)` / `renderer.endWindow()` with `batch.drawText(font, ..., x, y, Color.WHITE)`
- Needs a `BitmapFont` reference — `UIService` owns a default font (loaded from a bundled TTF in resources) and passes it to `UIBatch`; components call `batch.getDefaultFont()` or receive it via constructor

**`SandboxApplication`**
- `new UIService()` — no renderer argument
- UIService still added last in `loadContent()` (correct draw order already established)

### Deleted

| File                                            | Reason                           |
|-------------------------------------------------|----------------------------------|
| `NuklearUIRenderer.java`                        | Entire Nuklear base class        |
| `NuklearRenderer.java`                          | HAL-backed Nuklear renderer      |
| `UIRenderer.java`                               | Interface only used by Nuklear   |

### Dependency removals

- Remove `requires org.lwjgl.nuklear` from `module-info.java`
- Remove Nuklear artifacts from `build.gradle.kts` LWJGL dependency block

## 3D Compatibility

When a 3D scene is introduced:

1. The 3D scene renders in its own pass (perspective, depth test on) — `ScreenManager` drives this
2. `UIService` is the last component in `components` (already enforced by adding it last in `loadContent()`)
3. `UIBatch.begin()` disables depth test before rendering UI quads
4. `UIBatch.end()` re-enables depth test so the next frame's state is clean

No architectural change is needed when 3D arrives. The UI layer is structurally isolated from the
scene pipeline.

## Todos

1. Add a `.ttf` font file to `resources/fonts/` (a permissively-licensed font, e.g. JetBrains Mono)
2. Create `BitmapFont` in `core/render/font/` using `stb_truetype` + `Texture2D.createFromPixels()`
3. Create `UIBatch` in `core/ui/` wrapping `SpriteBatch2D` with screen-space ortho + depth test management
4. Change `UIComponent.draw` / `UICanvas` / `UIObject` to accept `UIBatch`
5. Refactor `UIService` — own `UIBatch` + default `BitmapFont`, remove `UIRenderer`
6. Rewrite `DebugStatsComponent` using `batch.drawText()`
7. Delete: `NuklearUIRenderer`, `NuklearRenderer`, `NuklearOpenGLRenderer`, `UIRenderer`
8. Delete Nuklear shader files
9. Remove Nuklear from `module-info.java` and `build.gradle.kts`
10. Update `SandboxApplication`
11. Run `spotlessApply`, build, verify debug stats visible
