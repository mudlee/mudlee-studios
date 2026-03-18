package hu.mudlee.core.ecs.system;

import hu.mudlee.core.ecs.ComponentMapper;
import hu.mudlee.core.ecs.ComponentMapperService;
import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.RenderSystemBase;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.render.SpriteRenderPass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpriteRender2DSystem extends RenderSystemBase {

    private ComponentMapper<Transform2DComponent> transformMapper;
    private ComponentMapper<Sprite2DComponent> spriteMapper;
    private final List<Entity> sortBuffer = new ArrayList<>();
    private Comparator<Entity> zComparator;

    public SpriteRender2DSystem() {}

    @Override
    public void initialize(ComponentMapperService mappers) {
        transformMapper = mappers.getMapper(Transform2DComponent.class);
        spriteMapper = mappers.getMapper(Sprite2DComponent.class);
        zComparator = Comparator.comparingInt(e -> transformMapper.get(e).z);
    }

    @Override
    public void render(SpriteRenderPass renderPass) {
        sortBuffer.clear();
        sortBuffer.addAll(em.getEntitiesWith(Transform2DComponent.class, Sprite2DComponent.class));
        sortBuffer.sort(zComparator);

        for (int i = 0; i < sortBuffer.size(); i++) {
            var entity = sortBuffer.get(i);
            var t = transformMapper.get(entity);
            var s = spriteMapper.get(entity);
            if (s.region == null) {
                continue;
            }
            renderPass.drawSprite(
                    s.region,
                    t.getWorldPosition(),
                    s.tint,
                    t.getWorldRotation(),
                    s.origin,
                    s.scale * t.getWorldScale().x,
                    s.flipX,
                    s.flipY);
        }
    }
}
