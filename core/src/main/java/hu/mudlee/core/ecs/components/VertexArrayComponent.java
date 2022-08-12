package hu.mudlee.core.ecs.components;

import com.badlogic.ashley.core.Component;
import hu.mudlee.core.render.VertexArray;

public record VertexArrayComponent(VertexArray vertexArray) implements Component {
}
