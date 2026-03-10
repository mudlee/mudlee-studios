package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;

public abstract class EntityProcessingSystem extends SystemBase {

    private final Aspect aspect;

    protected EntityProcessingSystem(Aspect aspect) {
        this.aspect = aspect;
    }

    @Override
    public final void update(GameTime gameTime) {
        var entities = em.getEntitiesWith(aspect.all);
        for (int i = 0; i < entities.size(); i++) {
            process(gameTime, entities.get(i));
        }
    }

    protected abstract void process(GameTime gameTime, Entity entity);
}
