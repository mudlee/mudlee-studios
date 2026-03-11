package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;
import java.util.ArrayList;
import java.util.List;

public final class SystemManager {

    private final List<SystemBase> updateSystems = new ArrayList<>();
    private final List<RenderSystemBase> renderSystems = new ArrayList<>();

    public void add(SystemBase system) {
        if (system instanceof RenderSystemBase r) {
            renderSystems.add(r);
        } else {
            updateSystems.add(system);
        }
    }

    public void updateAll(GameTime gameTime) {
        for (int i = 0; i < updateSystems.size(); i++) {
            updateSystems.get(i).update(gameTime);
        }
    }

    public void renderAll(RenderContext context) {
        for (int i = 0; i < renderSystems.size(); i++) {
            renderSystems.get(i).render(context);
        }
    }

    public void disposeAll() {
        for (int i = 0; i < updateSystems.size(); i++) {
            updateSystems.get(i).onDispose();
        }
        for (int i = 0; i < renderSystems.size(); i++) {
            renderSystems.get(i).onDispose();
        }
        updateSystems.clear();
        renderSystems.clear();
    }
}
