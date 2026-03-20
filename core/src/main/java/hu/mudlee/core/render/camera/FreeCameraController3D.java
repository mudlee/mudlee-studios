package hu.mudlee.core.render.camera;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.input.InputAction;
import hu.mudlee.core.input.Mouse;
import hu.mudlee.core.window.CursorMode;
import hu.mudlee.core.window.Window;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Free-fly 3D camera controller driven by the engine input action system.
 */
public final class FreeCameraController3D implements Disposable {

    private final PerspectiveCamera3D camera;
    private final InputAction moveAction;
    private final InputAction lookAction;
    private final InputAction riseAction;
    private final InputAction fallAction;
    private final InputAction toggleMouseCaptureAction;
    private final InputAction sprintAction;
    private final Vector2f lookInput = new Vector2f();
    private final Vector3f moveDelta = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();

    private float moveSpeed = 6f;
    private float sprintMultiplier = 2.5f;
    private float mouseSensitivity = 0.0025f;
    private float gamepadLookSpeed = 2.5f;
    private boolean mouseCaptured;

    public FreeCameraController3D(
            PerspectiveCamera3D camera,
            InputAction moveAction,
            InputAction lookAction,
            InputAction riseAction,
            InputAction fallAction,
            InputAction toggleMouseCaptureAction,
            InputAction sprintAction) {
        this.camera = camera;
        this.moveAction = moveAction;
        this.lookAction = lookAction;
        this.riseAction = riseAction;
        this.fallAction = fallAction;
        this.toggleMouseCaptureAction = toggleMouseCaptureAction;
        this.sprintAction = sprintAction;
        if (toggleMouseCaptureAction != null) {
            toggleMouseCaptureAction.onPerformed(ctx -> setMouseCaptured(!mouseCaptured));
        }
    }

    public void setMoveSpeed(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public void setSprintMultiplier(float sprintMultiplier) {
        this.sprintMultiplier = sprintMultiplier;
    }

    public void setMouseSensitivity(float mouseSensitivity) {
        this.mouseSensitivity = mouseSensitivity;
    }

    public void setGamepadLookSpeed(float gamepadLookSpeed) {
        this.gamepadLookSpeed = gamepadLookSpeed;
    }

    public boolean isMouseCaptured() {
        return mouseCaptured;
    }

    public void toggleMouseCapture() {
        setMouseCaptured(!mouseCaptured);
    }

    public void setMouseCaptured(boolean mouseCaptured) {
        this.mouseCaptured = mouseCaptured;
        Window.setCursorMode(mouseCaptured ? CursorMode.DISABLED : CursorMode.NORMAL);
    }

    public void setMouseCaptureEnabled(boolean enabled) {
        setMouseCaptured(enabled);
    }

    public void update(GameTime gameTime) {
        updateRotation(gameTime);
        updateMovement(gameTime);
    }

    private void updateRotation(GameTime gameTime) {
        var mouseState = Mouse.getState();
        var yaw = camera.getYaw();
        var pitch = camera.getPitch();

        if (mouseCaptured) {
            yaw += mouseState.deltaX() * mouseSensitivity;
            pitch -= mouseState.deltaY() * mouseSensitivity;
        }

        if (lookAction != null) {
            lookInput.set(lookAction.readVector2());
            yaw += lookInput.x * gamepadLookSpeed * gameTime.elapsedSeconds();
            pitch += lookInput.y * gamepadLookSpeed * gameTime.elapsedSeconds();
        }

        camera.setRotation(yaw, pitch);
    }

    private void updateMovement(GameTime gameTime) {
        moveDelta.zero();

        if (moveAction != null) {
            var moveInput = moveAction.readVector2();
            camera.getForward(forward).y = 0f;
            if (forward.lengthSquared() > 0f) {
                forward.normalize();
            }
            camera.getRight(right).y = 0f;
            if (right.lengthSquared() > 0f) {
                right.normalize();
            }
            moveDelta.fma(moveInput.y, forward).fma(moveInput.x, right);
        }

        if (riseAction != null && riseAction.isPressed()) {
            moveDelta.y += 1f;
        }
        if (fallAction != null && fallAction.isPressed()) {
            moveDelta.y -= 1f;
        }

        if (moveDelta.lengthSquared() == 0f) {
            return;
        }

        var speed = moveSpeed;
        if (sprintAction != null && sprintAction.isPressed()) {
            speed *= sprintMultiplier;
        }

        moveDelta.normalize().mul(speed * gameTime.elapsedSeconds());
        camera.translate(moveDelta);
    }

    @Override
    public void dispose() {
        if (mouseCaptured) {
            setMouseCaptured(false);
        }
    }
}
