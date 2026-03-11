package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.RenderContext;

public abstract class RenderSystemBase extends SystemBase {

    protected RenderSystemBase(EntityManager em) {
        super(em);
    }

    protected RenderSystemBase() {}

    public abstract void render(RenderContext context);

    @Override
    public final void update(GameTime gameTime) {}
}
