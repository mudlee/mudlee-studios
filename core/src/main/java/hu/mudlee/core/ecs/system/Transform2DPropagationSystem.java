package hu.mudlee.core.ecs.system;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.ComponentMapper;
import hu.mudlee.core.ecs.ComponentMapperService;
import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.SystemBase;
import hu.mudlee.core.ecs.component.Transform2DComponent;

/**
 * Propagates world-space transforms from parent to child.
 *
 * <p>Add this system before any rendering or physics system so that world transforms are up to
 * date when consumed.
 */
public final class Transform2DPropagationSystem extends SystemBase {

    private ComponentMapper<Transform2DComponent> transformMapper;

    @Override
    public void initialize(ComponentMapperService mappers) {
        transformMapper = mappers.getMapper(Transform2DComponent.class);
    }

    @Override
    public void update(GameTime gameTime) {
        var entities = em.getEntitiesWith(Transform2DComponent.class);
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.get(i);
            var t = transformMapper.get(entity);
            if (t.getParent() == null) {
                t.propagateRoot();
                propagateChildren(entity, t);
            }
        }
    }

    private void propagateChildren(Entity parent, Transform2DComponent parentTransform) {
        var entities = em.getEntitiesWith(Transform2DComponent.class);
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.get(i);
            var t = transformMapper.get(entity);
            if (t.getParent() != null && t.getParent().id() == parent.id()) {
                t.propagateFrom(parentTransform);
                propagateChildren(entity, t);
            }
        }
    }
}
