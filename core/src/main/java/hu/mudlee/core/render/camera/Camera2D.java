package hu.mudlee.core.render.camera;

import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * Base class for all 2D cameras.
 *
 * <p>Holds the common 2D camera state: world {@link #position} the camera is centred on,
 * {@link #zoom} multiplier, and {@link #rotation} in radians. Subclasses decide how to compute
 * the combined projection × view matrix returned by {@link #getTransformMatrix()}.
 *
 * <p>Pass the result to {@link hu.mudlee.core.render.SpriteBatch2D#begin(Matrix4f)}.
 *
 * <p>Uses a dirty flag pattern: the transform matrix is only recomputed when position, zoom, or
 * rotation changes. Call {@link #markDirty()} after modifying these fields, or use the setter
 * methods which mark dirty automatically.
 */
public abstract class Camera2D {

    private final Vector2f position = new Vector2f();
    private float zoom = 1f;
    private float rotation = 0f;
    protected boolean dirty = true;

    /** Returns a read-only view of the position. To move the camera use {@link #setPosition}. */
    public Vector2f getPosition() {
        return position;
    }

    public float getZoom() {
        return zoom;
    }

    public float getRotation() {
        return rotation;
    }

    /** Marks the camera transform as needing recalculation. */
    public void markDirty() {
        dirty = true;
    }

    /** Sets position and marks the transform as dirty. */
    public void setPosition(float x, float y) {
        position.set(x, y);
        dirty = true;
    }

    /** Sets zoom and marks the transform as dirty. */
    public void setZoom(float zoom) {
        this.zoom = zoom;
        dirty = true;
    }

    /** Sets rotation and marks the transform as dirty. */
    public void setRotation(float rotation) {
        this.rotation = rotation;
        dirty = true;
    }

    /**
     * Returns the combined projection × view matrix for this camera.
     * Pass it directly to {@link hu.mudlee.core.render.SpriteBatch2D#begin(Matrix4f)}.
     */
    public abstract Matrix4f getTransformMatrix();
}
