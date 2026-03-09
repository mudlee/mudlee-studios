package hu.mudlee.core.ecs.component;

import hu.mudlee.core.ecs.Component;
import org.joml.Vector2f;

public final class Transform2DComponent implements Component {

    public final Vector2f position = new Vector2f();
    public float rotation = 0f;
    public final Vector2f scale = new Vector2f(1f, 1f);
    public int z = 0;
}
