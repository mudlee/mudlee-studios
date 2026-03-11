package hu.mudlee.core.ui;

import hu.mudlee.core.GameTime;

/**
 * Base class for all UI components attached to a {@link UIObject}.
 *
 * <p>Lifecycle (driven by {@link UIObject}):
 *
 * <ol>
 *   <li>{@link #start()} — once, when the owning {@link UIObject#start()} is called
 *   <li>{@link #update(GameTime)} — every frame
 *   <li>{@link #draw(UIBatch)} — every frame, during the UI draw pass
 *   <li>{@link #dispose()} — when the component is removed or its {@link UIObject} is destroyed
 * </ol>
 *
 * <p>Override only the methods you need; all have empty default implementations.
 */
public abstract class UIComponent {

    UIObject uiObject;

    public void start() {}

    public void update(GameTime gameTime) {}

    public void draw(UIBatch batch) {}

    public void dispose() {}

    public UIObject getUIObject() {
        return uiObject;
    }

    protected <T extends UIComponent> T getComponent(Class<T> type) {
        if (uiObject == null) {
            return null;
        }
        return uiObject.getComponent(type);
    }
}
