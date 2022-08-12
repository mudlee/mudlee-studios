package hu.mudlee.core.ecs.components;

import com.badlogic.ashley.core.Component;
import hu.mudlee.core.render.Shader;

public record ShaderComponent(Shader shader) implements Component {
}