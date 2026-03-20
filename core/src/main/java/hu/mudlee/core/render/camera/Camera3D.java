package hu.mudlee.core.render.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class Camera3D {

    private static final float MAX_PITCH = (float) Math.toRadians(89.0);

    private final Vector3f position = new Vector3f();
    private final Vector3f forward = new Vector3f(0f, 0f, -1f);
    private final Vector3f right = new Vector3f(1f, 0f, 0f);
    private final Vector3f up = new Vector3f(0f, 1f, 0f);
    private final Vector3f worldUp = new Vector3f(0f, 1f, 0f);
    private float yaw;
    private float pitch;
    protected boolean dirty = true;

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Vector3f getPosition(Vector3f dest) {
        return dest.set(position);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        dirty = true;
    }

    public void setPosition(Vector3f value) {
        position.set(value);
        dirty = true;
    }

    public void translate(float x, float y, float z) {
        position.add(x, y, z);
        dirty = true;
    }

    public void translate(Vector3f delta) {
        position.add(delta);
        dirty = true;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        dirty = true;
    }

    public void setPitch(float pitch) {
        this.pitch = clampPitch(pitch);
        dirty = true;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = clampPitch(pitch);
        dirty = true;
    }

    public void rotate(float deltaYaw, float deltaPitch) {
        setRotation(yaw + deltaYaw, pitch + deltaPitch);
    }

    public Vector3f getForward(Vector3f dest) {
        updateBasisVectors();
        return dest.set(forward);
    }

    public Vector3f getRight(Vector3f dest) {
        updateBasisVectors();
        return dest.set(right);
    }

    public Vector3f getUp(Vector3f dest) {
        updateBasisVectors();
        return dest.set(up);
    }

    protected Vector3f positionRef() {
        return position;
    }

    protected Vector3f forwardRef() {
        updateBasisVectors();
        return forward;
    }

    protected Vector3f upRef() {
        updateBasisVectors();
        return up;
    }

    protected void markDirty() {
        dirty = true;
    }

    public abstract Matrix4f getProjectionMatrix();

    public abstract Matrix4f getViewMatrix();

    private void updateBasisVectors() {
        forward.set((float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float)
                        (-Math.cos(yaw) * Math.cos(pitch)))
                .normalize();
        forward.cross(worldUp, right).normalize();
        right.cross(forward, up).normalize();
    }

    protected float clampPitch(float value) {
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, value));
    }
}
