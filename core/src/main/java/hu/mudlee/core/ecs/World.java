package hu.mudlee.core.ecs;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;

public final class World implements Disposable {

    public final EntityManager entities = new EntityManager();
    private final SystemManager systems = new SystemManager();

    public void addSystem(SystemBase system) {
        system.onStart();
        systems.add(system);
    }

    public void update(GameTime gameTime) {
        systems.updateAll(gameTime);
    }

    public void render(RenderContext context) {
        systems.renderAll(context);
    }

    @Override
    public void dispose() {
        systems.disposeAll();
    }
}
