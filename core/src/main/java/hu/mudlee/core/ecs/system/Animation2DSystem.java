package hu.mudlee.core.ecs.system;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.Aspect;
import hu.mudlee.core.ecs.ComponentMapper;
import hu.mudlee.core.ecs.ComponentMapperService;
import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.EntityProcessingSystem;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;

public final class Animation2DSystem extends EntityProcessingSystem {

    private ComponentMapper<Animation2DComponent> animMapper;
    private ComponentMapper<Sprite2DComponent> spriteMapper;

    public Animation2DSystem() {
        super(Aspect.all(Animation2DComponent.class, Sprite2DComponent.class));
    }

    @Override
    public void initialize(ComponentMapperService mappers) {
        animMapper = mappers.getMapper(Animation2DComponent.class);
        spriteMapper = mappers.getMapper(Sprite2DComponent.class);
    }

    @Override
    protected void process(GameTime gameTime, Entity entity) {
        var anim = animMapper.get(entity);
        var sprite = spriteMapper.get(entity);
        anim.player.update(gameTime);
        sprite.region = anim.player.getCurrentFrame();
    }
}
