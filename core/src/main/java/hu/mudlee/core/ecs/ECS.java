package hu.mudlee.core.ecs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECS {
    private static final Logger log = LoggerFactory.getLogger(ECS.class);
    private static ECS instance;
    private final Engine engine = new Engine();

    private ECS() {}

    public static ECS get() {
        if (instance == null) {
            instance = new ECS();
        }

        return instance;
    }

    public static void addSystem(EntitySystem system) {
        log.debug("Registering ECS system: {}", system.getClass().getSimpleName());
        get().engine.addSystem(system);
    }

    public static void addEntity(Entity entity) {
        get().engine.addEntity(entity);
    }

    public static void removeEntity(Entity entity) {
        get().engine.removeEntity(entity);
    }

    public static void removeAllEntities() {
        get().engine.removeAllEntities();
    }

    public static void update(float delta) {
        get().engine.update(delta);
    }

    public static ImmutableArray<Entity> getEntities() {
        return get().engine.getEntities();
    }
}
