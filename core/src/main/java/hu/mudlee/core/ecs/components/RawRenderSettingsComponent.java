package hu.mudlee.core.ecs.components;

import com.badlogic.ashley.core.Component;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;

public record RawRenderSettingsComponent(RenderMode renderMode, PolygonMode polygonMode)
    implements Component {}
