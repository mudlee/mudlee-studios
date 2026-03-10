package hu.mudlee.core.ecs;

public final class ComponentMapper<T extends Component> {

    private final EntityManager em;
    private final Class<T> type;

    ComponentMapper(EntityManager em, Class<T> type) {
        this.em = em;
        this.type = type;
    }

    public T get(Entity entity) {
        return em.getComponent(entity, type);
    }

    public boolean has(Entity entity) {
        return em.hasComponent(entity, type);
    }
}
