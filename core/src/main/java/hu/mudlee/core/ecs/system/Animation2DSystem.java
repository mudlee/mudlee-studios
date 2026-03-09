package hu.mudlee.core.ecs.system;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.EntityManager;
import hu.mudlee.core.ecs.SystemBase;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;

public final class Animation2DSystem extends SystemBase {

    public Animation2DSystem(EntityManager em) {
        super(em);
    }

    @Override
    public void update(GameTime gameTime) {
        for (var entity : em.getEntitiesWith(Animation2DComponent.class, Sprite2DComponent.class)) {
            var anim = em.getComponent(entity, Animation2DComponent.class);
            var sprite = em.getComponent(entity, Sprite2DComponent.class);
            anim.player.update(gameTime);
            sprite.region = anim.player.getCurrentFrame();
        }
    }
}
