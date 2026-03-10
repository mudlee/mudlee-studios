package hu.mudlee.core.ecs;

public final class ComponentMapperService {

    private final EntityManager em;

    ComponentMapperService(EntityManager em) {
        this.em = em;
    }

    public <T extends Component> ComponentMapper<T> getMapper(Class<T> type) {
        return new ComponentMapper<>(em, type);
    }
}
