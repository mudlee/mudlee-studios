package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;

public abstract class SystemBase {

    protected final EntityManager em;

    protected SystemBase(EntityManager em) {
        this.em = em;
    }

    public void onStart() {}

    public abstract void update(GameTime gameTime);

    public void onDispose() {}
}
