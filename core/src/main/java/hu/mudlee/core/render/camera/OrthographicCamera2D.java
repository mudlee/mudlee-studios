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
 *
 * <p>Uses a dirty flag pattern: the matrix is only recomputed when camera properties or window
 * size changes.
 */
public final class OrthographicCamera2D extends Camera2D {

    private final Matrix4f transformMatrix = new Matrix4f();
    private int cachedWindowWidth = -1;
    private int cachedWindowHeight = -1;

    @Override
    public Matrix4f getTransformMatrix() {
        var size = Window.getSize();

        // Check if window size changed
        if (size.x != cachedWindowWidth || size.y != cachedWindowHeight) {
            cachedWindowWidth = size.x;
            cachedWindowHeight = size.y;
            dirty = true;
        }

        if (!dirty) {
            return transformMatrix;
        }

        float hw = size.x / 2f;
        float hh = size.y / 2f;

        var z = getZoom();
        var r = getRotation();
        var pos = getPosition();

        transformMatrix
                .setOrtho(0f, size.x, 0f, size.y, -1f, 1f)
                .translate(hw, hh, 0f)
                .scale(z, z, 1f)
                .rotateZ(r)
                .translate(-pos.x, -pos.y, 0f);

        dirty = false;
        return transformMatrix;
    }
}
