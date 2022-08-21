package hu.mudlee.core.ecs.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import hu.mudlee.core.ecs.components.RawRenderSettingsComponent;
import hu.mudlee.core.ecs.components.ShaderComponent;
import hu.mudlee.core.ecs.components.VertexArrayComponent;
import hu.mudlee.core.render.Renderer;

public class RawRenderableSystem extends IteratingSystem {
  private ComponentMapper<VertexArrayComponent> vaoMapper = ComponentMapper.getFor(VertexArrayComponent.class);
  private ComponentMapper<ShaderComponent> shaderMapper = ComponentMapper.getFor(ShaderComponent.class);
  private ComponentMapper<RawRenderSettingsComponent> settingsMapper = ComponentMapper.getFor(RawRenderSettingsComponent.class);

  public RawRenderableSystem() {
    super(Family.all(VertexArrayComponent.class, ShaderComponent.class, RawRenderSettingsComponent.class).get());
  }

  @Override
  protected void processEntity(Entity entity, float deltaTime) {
    final var vaoC = vaoMapper.get(entity);
    final var shaderC = shaderMapper.get(entity);
    final var settingsC = settingsMapper.get(entity);
    Renderer.renderRaw(vaoC.vertexArray(), shaderC.shader(), settingsC.renderMode(), settingsC.polygonMode());
  }
}
