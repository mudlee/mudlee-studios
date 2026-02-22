package hu.mudlee.core.render.camera;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class Camera2D {
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    public final Vector2f position = new Vector2f();

    public Camera2D() {
        adjustProjection();
    }

    public void adjustProjection() {
        projectionMatrix.setOrtho(0.0f, 32.0f * 40.0f, 0.0f, 32.0f * 21.0f, 0.0f, 100.0f);
    }

    public Matrix4f getViewMatrix() {
        final var cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
        final var cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

        viewMatrix.setLookAt(
                new Vector3f(position.x, position.y, 20.0f), cameraFront.add(position.x, position.y, 0.0f), cameraUp);

        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }
}
