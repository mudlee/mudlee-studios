package hu.mudlee.core.ui;

import hu.mudlee.core.GameTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the retained tree of {@link UIObject}s for a single screen.
 *
 * <p>Mirrors the role of {@link hu.mudlee.core.gameobject.GameScene2D} for game objects. Owned and
 * driven by {@link UIService}.
 *
 * <pre>
 * var debugObj = uiService.getCanvas().create();
 * debugObj.addComponent(new DebugStatsComponent());
 * </pre>
 */
public final class UICanvas {

    private final List<UIObject> objects = new ArrayList<>();

    /** Creates a new {@link UIObject}, tracks it, and returns it for component attachment. */
    public UIObject create() {
        var object = new UIObject();
        objects.add(object);
        return object;
    }

    public void start() {
        for (int i = 0; i < objects.size(); i++) {
            objects.get(i).start();
        }
    }

    public void update(GameTime gameTime) {
        for (int i = 0; i < objects.size(); i++) {
            objects.get(i).update(gameTime);
        }
    }

    public void draw(UIBatch batch) {
        for (int i = 0; i < objects.size(); i++) {
            objects.get(i).draw(batch);
        }
    }

    public void dispose() {
        for (int i = objects.size() - 1; i >= 0; i--) {
            objects.get(i).dispose();
        }
        objects.clear();
    }
}
