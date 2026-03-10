package hu.mudlee.core.ecs.system;

import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.EntityManager;
import hu.mudlee.core.ecs.RenderSystemBase;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.render.RenderContext;
import hu.mudlee.core.render.SpriteBatch2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpriteRender2DSystem extends RenderSystemBase {

    private final List<Entity> sortBuffer = new ArrayList<>();

    public SpriteRender2DSystem(EntityManager em) {
        super(em);
    }

    @Override
    public void render(RenderContext context) {
        if (!(context instanceof SpriteBatch2D batch)) {
            return;
        }

        sortBuffer.clear();
        sortBuffer.addAll(em.getEntitiesWith(Transform2DComponent.class, Sprite2DComponent.class));
        sortBuffer.sort(Comparator.comparingInt(e -> em.getComponent(e, Transform2DComponent.class).z));

        for (int i = 0; i < sortBuffer.size(); i++) {
            var entity = sortBuffer.get(i);
            var t = em.getComponent(entity, Transform2DComponent.class);
            var s = em.getComponent(entity, Sprite2DComponent.class);
            if (s.region == null) {
                continue;
            }
            batch.draw(s.region, t.position, s.tint, t.rotation, s.origin, s.scale * t.scale.x, s.flipX, s.flipY);
        }
    }
}
