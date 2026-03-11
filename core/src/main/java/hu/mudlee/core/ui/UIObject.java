package hu.mudlee.core.ui;

import hu.mudlee.core.GameTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A UI entity that holds a set of {@link UIComponent} instances.
 *
 * <p>Mirrors {@link hu.mudlee.core.gameobject.GameObject} for the UI layer. Created and managed by
 * {@link UICanvas}; do not instantiate directly.
 *
 * <pre>
 * var debugObj = canvas.create();
 * debugObj.addComponent(new DebugStatsComponent());
 * </pre>
 */
public final class UIObject {

    public final UITransform transform = new UITransform();

    private final List<UIComponent> components = new ArrayList<>(4);

    public UIObject addComponent(UIComponent component) {
        component.uiObject = this;
        components.add(component);
        return this;
    }

    public <T extends UIComponent> T getComponent(Class<T> type) {
        for (int i = 0; i < components.size(); i++) {
            var c = components.get(i);
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    public <T extends UIComponent> boolean hasComponent(Class<T> type) {
        for (int i = 0; i < components.size(); i++) {
            if (type.isInstance(components.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void start() {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).start();
        }
    }

    public void update(GameTime gameTime) {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).update(gameTime);
        }
    }

    public void draw(UIBatch batch) {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).draw(batch);
        }
    }

    public void dispose() {
        for (int i = components.size() - 1; i >= 0; i--) {
            components.get(i).dispose();
        }
        components.clear();
    }
}
