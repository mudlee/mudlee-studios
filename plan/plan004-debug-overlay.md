# Plan 004 — UI System with Dear ImGui (Debug Overlay + Future UI)

## Goal

Integrate Dear ImGui as the UI backend behind an abstract `UIRenderer` interface, so the engine is never directly coupled to ImGui. UI elements are authored as `UIObject`s with `UIComponent`s — consistent with how `GameObject`/`Component` works. First use: debug overlay (FPS, frame time, memory). Future use: standard UI elements (buttons, text fields, panels).

---

## Chosen Library: Dear ImGui via imgui-java (SpaiR)

- **Artifact**: `io.github.spair:imgui-java-binding` + `io.github.spair:imgui-java-lwjgl3`
- **Latest version**: 1.90.0 (released 2025, actively maintained)
- **GitHub**: https://github.com/SpaiR/imgui-java
- **Why**: Industry-standard immediate-mode GUI for game engines. Feature-rich (graphs, sliders, dockable windows). Actively maintained. Vulkan backend is wirable manually.

### JPMS / Module System Note
imgui-java ships no `module-info.java` and no `Automatic-Module-Name` in its manifest. When placed on the module path it becomes an **automatic module** whose name is derived from the JAR filename: `imgui.java.binding` and `imgui.java.lwjgl3`. These names must be used in `module-info.java` `requires` directives. This is fragile if SpaiR renames the JAR — verify the actual name after adding the dependency by inspecting the resolved JAR filename.

---

## Architecture

Two distinct layers, clearly separated:

```
Game (via UIService)
  └── UICanvas                      [retained tree of UIObjects]
        └── UIObject                [has UIComponents + UITransform]
              └── UIComponent       [base class — draw(UIRenderer)]
                    └── e.g. DebugStatsComponent, TextComponent, ButtonComponent

  └── UIRenderer  [interface — thin backend API]
        └── ImGuiUIRenderer         [implementation — knows about ImGui]
              ├── ImGuiImplGlfw     (GLFW platform backend — input/window)
              └── ImGuiImplVulkan   (Vulkan renderer backend)
```

**Key principles:**
- `UIObject + UIComponent` = retained model. The user authors UI by adding components to objects — same mental model as `GameObject`.
- `UIRenderer` = thin immediate-mode backend. Each frame, `UIComponent.draw(UIRenderer)` calls `UIRenderer.text(...)`, `UIRenderer.button(...)` etc. — the immediate-mode calls happen inside the component, driven by the retained tree.
- No engine or game code imports ImGui classes — only `ImGuiUIRenderer` does.
- `UIComponent.draw` takes a `UIRenderer`, not a `SpriteBatch` — it is a separate base class from `gameobject.Component`, but mirrors its lifecycle pattern exactly.

This mirrors the existing HAL pattern: just as `Renderer` hides `VulkanContext`, `UIRenderer` hides `ImGuiUIRenderer`.

---

## Step-by-Step Implementation Plan

### Step 1 — Add the dependency

In `build.gradle.kts` (core module):
- Add `io.github.spair:imgui-java-binding:1.90.0`
- Add `io.github.spair:imgui-java-lwjgl3:1.90.0` (provides `ImGuiImplGlfw` and `ImGuiImplGl3`)
- imgui-java ships its own natives inside the JAR — no separate native artifact needed.

### Step 2 — Update `module-info.java`

In `core/src/main/java/module-info.java`:
```java
requires imgui.binding;
requires imgui.lwjgl3;
```
> The automatic module names are derived from the JAR filenames by JPMS: `imgui-java-binding-1.90.0.jar` → `imgui.binding`, `imgui-java-lwjgl3-1.90.0.jar` → `imgui.lwjgl3`. Verified via `jar --describe-module`.

### Step 3 — Define the `UIRenderer` interface

Create `core/src/main/java/hu/mudlee/core/ui/UIRenderer.java`.

Lifecycle methods:
```java
void initialize(long windowHandle);
void newFrame();
void render();
void resize(int width, int height);
void dispose();
```

Widget methods (add as needed — start with what the debug overlay requires):
```java
void beginWindow(String title);
void endWindow();
void text(String label);
void textColored(float r, float g, float b, float a, String label);
void separator();
boolean button(String label);
void plotLines(String label, float[] values);
```

This interface is the only UI API the rest of the engine and games ever see. No `imgui.*` imports outside of `ImGuiUIRenderer`.

### Step 4 — Define `UIComponent` and `UIObject`

**`UIComponent`** — base class mirroring `gameobject.Component`, but for UI:

```java
// core/.../ui/UIComponent.java
public abstract class UIComponent {
    UIObject uiObject;

    public void start() {}
    public void update(GameTime gameTime) {}
    public void draw(UIRenderer renderer) {}
    public void dispose() {}

    public UIObject getUIObject() { return uiObject; }

    protected <T extends UIComponent> T getComponent(Class<T> type) {
        return uiObject.getComponent(type);
    }
}
```

**`UITransform`** — position, size, and anchor for a UI element:

```java
// core/.../ui/UITransform.java
public final class UITransform {
    public float x, y;
    public float width, height;
    // anchor / pivot can be added later
}
```

**`UIObject`** — a UI entity that holds components, mirroring `GameObject`:

```java
// core/.../ui/UIObject.java
public final class UIObject {
    public final UITransform transform = new UITransform();
    private final List<UIComponent> components = new ArrayList<>();

    public void addComponent(UIComponent component) { ... }
    public <T extends UIComponent> T getComponent(Class<T> type) { ... }
    public void start() { /* calls start() on all components */ }
    public void update(GameTime gameTime) { /* calls update on all components */ }
    public void draw(UIRenderer renderer) { /* calls draw on all components */ }
    public void dispose() { /* calls dispose on all components */ }
}
```

### Step 5 — Implement `ImGuiUIRenderer`

Create `core/src/main/java/hu/mudlee/core/ui/ImGuiUIRenderer.java` implementing `UIRenderer`.

`initialize(long windowHandle)`:
- Call `ImGui.createContext()`
- Configure `ImGuiIO`: enable docking, set font, set style
- Init GLFW platform backend: `ImGuiImplGlfw.init(windowHandle, true)`
- Init renderer backend: `ImGuiImplVulkan.init(...)` (passing Vulkan handles: instance, device, render pass, command pool)

`newFrame()`:
- `ImGuiImplGlfw.newFrame()`
- `ImGuiImplVulkan.newFrame()`
- `ImGui.newFrame()`

`render()`:
- `ImGui.render()`
- `ImGuiImplVulkan.renderDrawData(ImGui.getDrawData())`

`dispose()`:
- `ImGuiImplVulkan.shutdown()`
- `ImGuiImplGlfw.shutdown()`
- `ImGui.destroyContext()`

Widget methods delegate to the corresponding `ImGui.*` static calls.

### Step 6 — Create `UICanvas`

Create `core/src/main/java/hu/mudlee/core/ui/UICanvas.java`.

Manages a list of `UIObject`s — the retained UI tree for a screen. Mirrors `GameScene`'s role for game objects:

```java
public final class UICanvas {
    private final List<UIObject> objects = new ArrayList<>();

    public UIObject create() { /* creates, tracks, and returns a new UIObject */ }
    public void start() { ... }
    public void update(GameTime gameTime) { ... }
    public void draw(UIRenderer renderer) { ... }
    public void dispose() { ... }
}
```

### Step 7 — Create `UIService`

Create `core/src/main/java/hu/mudlee/core/ui/UIService.java` extending `GameService`.

- Holds a `UIRenderer` and a `UICanvas`.
- `initialize`: calls `renderer.initialize(Window.getHandle())`
- `update(GameTime)`: calls `renderer.newFrame()`, then `canvas.update(gameTime)`
- `draw(GameTime)`: calls `canvas.draw(renderer)`, then `renderer.render()` — so all component draws happen between `newFrame` and `render`
- `resize(int w, int h)`: calls `renderer.resize(w, h)`
- `dispose()`: calls `canvas.dispose()`, then `renderer.dispose()`

Register once at startup:
```java
var uiService = new UIService(new ImGuiUIRenderer());
components.add(uiService);
```

### Step 8 — Create `DebugStatsComponent`

Create `core/src/main/java/hu/mudlee/core/ui/DebugStatsComponent.java` extending `UIComponent`.

- `update(GameTime)`: collect FPS (rolling average over 60 frames), frame time (ms), heap used/max (MB), and draw call count. No per-frame allocations.
- `draw(UIRenderer)`: call `UIRenderer` widget methods to render a stats window.

```
┌─ Debug ──────────────────┐
│ FPS        144.0         │
│ Frame time   6.9 ms      │
│ Heap       128 / 512 MB  │
│ Draw calls  42           │
└──────────────────────────┘
```

Toggle visibility with `F3` via `InputSystem`.

#### Draw call tracking

All GPU draw calls (`vkCmdDraw*`) funnel through `Renderer.renderRaw()`. Add a counter there:

1. **Add to `Renderer`**: a static `int drawCallCount` field and a `static int getDrawCallCount()` accessor.
2. **Increment** in `Renderer.renderRaw()` — one increment per call, regardless of backend.
3. **Reset** at the start of each frame — in `Renderer.clear()` or at the top of `Game.loop()`'s frame body, before updates run: `Renderer.resetDrawCallCount()`.
4. **Read** in `DebugStatsComponent.update()`: `Renderer.getDrawCallCount()`.

This keeps the counter entirely inside `Renderer` with no changes to `GraphicsContext` implementations.

Usage in sandbox:
```java
var debugObject = uiService.getCanvas().create();
debugObject.addComponent(new DebugStatsComponent());
```

### Step 9 — Export the new package

Add `exports hu.mudlee.core.ui;` to `core/src/main/java/module-info.java`.

---

## Files to Create / Modify

| Action | File                                                                                               |
|--------|----------------------------------------------------------------------------------------------------|
| Modify | `build.gradle.kts` — add imgui-java-binding + imgui-java-lwjgl3                                    |
| Modify | `core/src/main/java/module-info.java` — add requires + exports for `hu.mudlee.core.ui`             |
| Create | `core/.../core/ui/UIRenderer.java` — backend interface                                             |
| Create | `core/.../core/ui/ImGuiUIRenderer.java` — Dear ImGui implementation                                |
| Create | `core/.../core/ui/UIComponent.java` — base class for UI components                                 |
| Create | `core/.../core/ui/UITransform.java` — position/size for UI objects                                 |
| Create | `core/.../core/ui/UIObject.java` — UI entity (holds UIComponents)                                  |
| Create | `core/.../core/ui/UICanvas.java` — manages the retained UIObject tree                              |
| Create | `core/.../core/ui/UIService.java` — GameService that drives the UI system                          |
| Create | `core/.../core/ui/DebugStatsComponent.java` — FPS/memory/draw call debug component                 |
| Modify | `core/.../core/Renderer.java` — add draw call counter (increment in `renderRaw`, reset each frame) |
| Modify | `sandbox/.../SandboxApplication.java` — register UIService, add DebugStatsComponent                |

---

## Swapping ImGui Later

To replace Dear ImGui with another library:
1. Create a new class implementing `UIRenderer`.
2. Change one line in `SandboxApplication`: `new UIService(new MyOtherUIRenderer())`.
3. No engine code changes required — `UIObject`, `UICanvas`, `UIComponent` are all backend-agnostic.

---

---

## Stats to Display (initial set)

- FPS (smoothed, 60-frame rolling average)
- Frame time (ms)
- Heap used / heap max (MB)
- Draw calls per frame (via `Renderer.getDrawCallCount()`)
- Future: entity count, active screen name
