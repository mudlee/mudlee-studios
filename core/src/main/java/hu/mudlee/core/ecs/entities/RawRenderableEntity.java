package hu.mudlee.core.ecs.entities;

import com.badlogic.ashley.core.Entity;
import hu.mudlee.core.ecs.components.RawRenderSettingsComponent;
import hu.mudlee.core.ecs.components.ShaderComponent;
import hu.mudlee.core.ecs.components.VertexArrayComponent;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawRenderableEntity extends Entity {
    private static final Logger LOG = LoggerFactory.getLogger(RawRenderableEntity.class);
    private final VertexArray vao;
    private final Shader shader;

    public RawRenderableEntity(
            String name, VertexArray vao, Shader shader, RenderMode renderMode, PolygonMode polygoneMode) {
        this.vao = vao;
        this.shader = shader;

        add(new VertexArrayComponent(vao));
        add(new ShaderComponent(shader));
        add(new RawRenderSettingsComponent(renderMode, polygoneMode));

        LOG.debug("Entity '{}'created", name);
    }
}
