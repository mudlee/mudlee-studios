package hu.mudlee.core.render.camera;

import hu.mudlee.core.window.Window;
import org.joml.Matrix4f;

/**
 * A screen-sized orthographic 2D camera.
 *
 * <p>The viewport always matches the current window size, so no manual resize handling is needed.
 * {@link #position} is the world coordinate centred on screen (MonoGame convention).
 *
 * <p>Combined matrix formula:
 * <pre>
 * ortho(0, screenW, 0, screenH) · T(screenW/2, screenH/2) · S(zoom) · R(rotation) · T(-position)
 * </pre>
 */
public final class OrthographicCamera2D extends Camera2D {

    private final Matrix4f transformMatrix = new Matrix4f();

    @Override
    public Matrix4f getTransformMatrix() {
        var size = Window.getSize();
        float hw = size.x / 2f;
        float hh = size.y / 2f;

        return transformMatrix
                .setOrtho(0f, size.x, 0f, size.y, -1f, 1f)
                .translate(hw, hh, 0f)
                .scale(zoom, zoom, 1f)
                .rotateZ(rotation)
                .translate(-position.x, -position.y, 0f);
    }
}
