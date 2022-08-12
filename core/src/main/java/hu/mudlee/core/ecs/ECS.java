package hu.mudlee.core.ecs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ECS {
  private static final Logger log = LoggerFactory.getLogger(ECS.class);
  private final Engine engine;

  public ECS(List<EntitySystem> systems) {
    engine = new Engine();
    systems.forEach(system -> {
      log.debug("Registering ECS system: {}", system.getClass().getSimpleName());
      engine.addSystem(system);
    });
  }

  public void addEntity(Entity entity) {
    engine.addEntity(entity);
  }

  public void update(float delta) {
    engine.update(delta);
  }
}
