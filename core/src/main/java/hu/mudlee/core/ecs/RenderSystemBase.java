package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.SpriteRenderPass;

public abstract class RenderSystemBase extends SystemBase {

    protected RenderSystemBase(EntityManager em) {
        super(em);
    }

    protected RenderSystemBase() {}

    public abstract void render(SpriteRenderPass renderPass);

    @Override
    public final void update(GameTime gameTime) {}
}
