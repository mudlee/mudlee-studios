package hu.mudlee.core.ecs.component;

import hu.mudlee.core.Color;
import hu.mudlee.core.ecs.Component;
import hu.mudlee.core.render.texture.TextureRegion;
import org.joml.Vector2f;

public final class Sprite2DComponent implements Component {

    public TextureRegion region;
    public Color tint = Color.WHITE;
    public float scale = 1f;
    public boolean flipX = false;
    public boolean flipY = false;
    public final Vector2f origin = new Vector2f();
}
