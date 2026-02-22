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
 * <p>Pass the result to {@link hu.mudlee.core.render.SpriteBatch#begin(Matrix4f)}.
 */
public abstract class Camera2D {

    public final Vector2f position = new Vector2f();
    public float zoom = 1f;
    public float rotation = 0f;

    /**
     * Returns the combined projection × view matrix for this camera.
     * Pass it directly to {@link hu.mudlee.core.render.SpriteBatch#begin(Matrix4f)}.
     */
    public abstract Matrix4f getTransformMatrix();
}
