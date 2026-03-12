package hu.mudlee.core.ecs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntityManager {

    private int nextId = 0;
    private long structureVersion = 0;
    private final Deque<Integer> freeIds = new ArrayDeque<>();

    private final Map<Class<? extends Component>, Map<Integer, Component>> byType = new HashMap<>();
    private final Map<Integer, Set<Class<? extends Component>>> byEntity = new HashMap<>();
    private final Map<AspectKey, CachedQuery> queryCache = new HashMap<>();

    public Entity createEntity() {
        int id = freeIds.isEmpty() ? nextId++ : freeIds.pop();
        byEntity.put(id, new HashSet<>());
        return new Entity(id);
    }

    public void destroyEntity(Entity entity) {
        var types = byEntity.remove(entity.id());
        if (types != null) {
            for (var type : types) {
                byType.get(type).remove(entity.id());
            }
        }
        freeIds.push(entity.id());
        structureVersion++;
    }

    public <T extends Component> void addComponent(Entity entity, T component) {
        byType.computeIfAbsent(component.getClass(), k -> new HashMap<>()).put(entity.id(), component);
        byEntity.get(entity.id()).add(component.getClass());
        structureVersion++;
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
        structureVersion++;
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
        var key = new AspectKey(required);
        var cached = queryCache.get(key);
        if (cached != null && cached.version == structureVersion) {
            return cached.entities;
        }
        var result = buildQuery(required);
        queryCache.put(key, new CachedQuery(structureVersion, result));
        return result;
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

    private static final class AspectKey {
        private final Class<? extends Component>[] types;
        private final int hashCode;

        @SafeVarargs
        AspectKey(Class<? extends Component>... types) {
            this.types = types.clone();
            Arrays.sort(this.types, (a, b) -> a.getName().compareTo(b.getName()));
            this.hashCode = Arrays.hashCode(this.types);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AspectKey other)) {
                return false;
            }
            return Arrays.equals(types, other.types);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private record CachedQuery(long version, List<Entity> entities) {}
}
