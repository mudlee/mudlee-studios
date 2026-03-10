package hu.mudlee.core.ecs;

import hu.mudlee.core.GameTime;

public abstract class SystemBase {

    protected EntityManager em;

    protected SystemBase(EntityManager em) {
        this.em = em;
    }

    protected SystemBase() {}

    void injectEntityManager(EntityManager em) {
        this.em = em;
    }

    public void initialize(ComponentMapperService mappers) {}

    public void onStart() {}

    public abstract void update(GameTime gameTime);

    public void onDispose() {}
}
