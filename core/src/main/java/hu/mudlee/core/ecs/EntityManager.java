package hu.mudlee.core.ecs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntityManager {

    private int nextId = 0;

    private final Map<Class<? extends Component>, Map<Integer, Component>> byType = new HashMap<>();
    private final Map<Integer, Set<Class<? extends Component>>> byEntity = new HashMap<>();
    private final Map<Set<Class<? extends Component>>, List<Entity>> queryCache = new HashMap<>();

    public Entity createEntity() {
        var e = new Entity(nextId++);
        byEntity.put(e.id(), new HashSet<>());
        return e;
    }

    public void destroyEntity(Entity entity) {
        var types = byEntity.remove(entity.id());
        if (types != null) {
            for (var type : types) {
                byType.get(type).remove(entity.id());
            }
        }
        queryCache.clear();
    }

    public <T extends Component> void addComponent(Entity entity, T component) {
        byType.computeIfAbsent(component.getClass(), k -> new HashMap<>()).put(entity.id(), component);
        byEntity.get(entity.id()).add(component.getClass());
        queryCache.clear();
    }

    public void removeComponent(Entity entity, Class<? extends Component> type) {
        var map = byType.get(type);
        if (map != null) {
            map.remove(entity.id());
        }
        var types = byEntity.get(entity.id());
        if (types != null) {
            types.remove(type);
        }
        queryCache.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Entity entity, Class<T> type) {
        var map = byType.get(type);
        var result = map == null ? null : (T) map.get(entity.id());
        if (result == null) {
            throw new IllegalStateException(
                    "Entity " + entity.id() + " missing required component: " + type.getSimpleName());
        }
        return result;
    }

    public boolean hasComponent(Entity entity, Class<? extends Component> type) {
        var map = byType.get(type);
        return map != null && map.containsKey(entity.id());
    }

    @SafeVarargs
    public final List<Entity> getEntitiesWith(Class<? extends Component>... required) {
        var key = new HashSet<Class<? extends Component>>(Arrays.asList(required));
        return queryCache.computeIfAbsent(key, k -> buildQuery(required));
    }

    @SafeVarargs
    private List<Entity> buildQuery(Class<? extends Component>... required) {
        Set<Integer> ids = null;
        for (var type : required) {
            var map = byType.get(type);
            if (map == null) {
                return List.of();
            }
            if (ids == null) {
                ids = new HashSet<>(map.keySet());
            } else {
                ids.retainAll(map.keySet());
            }
            if (ids.isEmpty()) {
                return List.of();
            }
        }
        if (ids == null) {
            return List.of();
        }
        var result = new ArrayList<Entity>(ids.size());
        for (var id : ids) {
            result.add(new Entity(id));
        }
        return Collections.unmodifiableList(result);
    }
}
