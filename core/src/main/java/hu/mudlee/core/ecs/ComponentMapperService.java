package hu.mudlee.core.ecs;

import java.util.HashMap;
import java.util.Map;

public final class ComponentMapperService {

    private final EntityManager em;
    private final Map<Class<?>, ComponentMapper<?>> cache = new HashMap<>();

    ComponentMapperService(EntityManager em) {
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> ComponentMapper<T> getMapper(Class<T> type) {
        return (ComponentMapper<T>) cache.computeIfAbsent(type, k -> new ComponentMapper<>(em, type));
    }
}
