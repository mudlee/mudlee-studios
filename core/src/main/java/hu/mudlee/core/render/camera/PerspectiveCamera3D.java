package hu.mudlee.core.render.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A cached perspective camera suitable for free-fly 3D scenes.
 */
public final class PerspectiveCamera3D extends Camera3D {

    public static final float DEFAULT_FOV_Y_RADIANS = (float) Math.toRadians(45.0);
    public static final float DEFAULT_NEAR_PLANE = 0.1f;
    public static final float DEFAULT_FAR_PLANE = 1000f;
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f target = new Vector3f();

    private float fieldOfViewY = DEFAULT_FOV_Y_RADIANS;
    private float aspectRatio = 16f / 9f;
    private float nearPlane = DEFAULT_NEAR_PLANE;
    private float farPlane = DEFAULT_FAR_PLANE;
    private boolean projectionDirty = true;
    private boolean viewProjectionDirty = true;

    public float getFieldOfViewY() {
        return fieldOfViewY;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        viewProjectionDirty = true;
    }

    @Override
    public void setPosition(float x, float y, float z) {
        super.setPosition(x, y, z);
        viewProjectionDirty = true;
    }

    @Override
    public void translate(float x, float y, float z) {
        super.translate(x, y, z);
        viewProjectionDirty = true;
    }

    @Override
    public void translate(Vector3f delta) {
        super.translate(delta);
        viewProjectionDirty = true;
    }

    @Override
    public void setYaw(float yaw) {
        super.setYaw(yaw);
        viewProjectionDirty = true;
    }

    @Override
    public void setPitch(float pitch) {
        super.setPitch(clampPitch(pitch));
        viewProjectionDirty = true;
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        super.setRotation(yaw, clampPitch(pitch));
        viewProjectionDirty = true;
    }

    public void setFieldOfViewY(float fieldOfViewY) {
        this.fieldOfViewY = fieldOfViewY;
        projectionDirty = true;
        viewProjectionDirty = true;
    }

    public void setAspectRatio(float aspectRatio) {
        if (aspectRatio <= 0f) {
            throw new IllegalArgumentException("Aspect ratio must be greater than zero");
        }
        this.aspectRatio = aspectRatio;
        projectionDirty = true;
        viewProjectionDirty = true;
    }

    public void setViewport(int width, int height) {
        if (height <= 0) {
            return;
        }
        setAspectRatio((float) width / (float) height);
    }

    public void resize(int width, int height) {
        setViewport(width, height);
    }

    public void setNearPlane(float nearPlane) {
        if (nearPlane <= 0f) {
            throw new IllegalArgumentException("Near plane must be greater than zero");
        }
        this.nearPlane = nearPlane;
        projectionDirty = true;
        viewProjectionDirty = true;
    }

    public void setFarPlane(float farPlane) {
        if (farPlane <= nearPlane) {
            throw new IllegalArgumentException("Far plane must be greater than near plane");
        }
        this.farPlane = farPlane;
        projectionDirty = true;
        viewProjectionDirty = true;
    }

    public void lookAt(float x, float y, float z) {
        target.set(x, y, z).sub(getPosition());
        if (target.lengthSquared() == 0f) {
            return;
        }
        target.normalize();
        var yaw = (float) Math.atan2(target.x, -target.z);
        var pitch = (float) Math.asin(target.y);
        setRotation(yaw, pitch);
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        if (!projectionDirty) {
            return projectionMatrix;
        }
        projectionMatrix.setPerspective(fieldOfViewY, aspectRatio, nearPlane, farPlane, true);
        projectionDirty = false;
        return projectionMatrix;
    }

    @Override
    public Matrix4f getViewMatrix() {
        if (!dirty) {
            return viewMatrix;
        }
        getForward(forward);
        getUp(up);
        target.set(getPosition()).add(forward);
        viewMatrix.setLookAt(getPosition(), target, up);
        dirty = false;
        return viewMatrix;
    }

    public Matrix4f getViewProjectionMatrix() {
        if (!viewProjectionDirty) {
            return viewProjectionMatrix;
        }
        viewProjectionMatrix.set(getProjectionMatrix()).mul(getViewMatrix());
        viewProjectionDirty = false;
        return viewProjectionMatrix;
    }
}
