package hu.mudlee.core.ecs.component;

import hu.mudlee.core.ecs.Component;
import hu.mudlee.core.render.camera.Camera2D;

public final class CameraComponent implements Component {

    public final Camera2D camera;
    public boolean active = true;

    public CameraComponent(Camera2D camera) {
        this.camera = camera;
    }
}
