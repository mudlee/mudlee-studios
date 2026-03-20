package hu.mudlee.sandbox;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.input.ActionType;
import hu.mudlee.core.input.GamepadAxis;
import hu.mudlee.core.input.GamepadButton;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.MeshRenderCoordinator;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.camera.FreeCameraController3D;
import hu.mudlee.core.render.camera.PerspectiveCamera3D;
import hu.mudlee.core.render.mesh.CubeMesh;
import hu.mudlee.core.render.mesh.Mesh3D;
import hu.mudlee.core.render.types.ShaderProps;
import hu.mudlee.core.window.CursorMode;
import hu.mudlee.core.window.Window;
import org.joml.Matrix4f;

public final class CubeScene implements Screen {

    private final Game game;
    private final GraphicsDevice graphicsDevice;

    private final Matrix4f modelMatrix = new Matrix4f();

    private MeshRenderCoordinator meshPass;
    private PerspectiveCamera3D camera;
    private FreeCameraController3D cameraController;
    private Shader shader;
    private Mesh3D cubeMesh;
    private InputActionMap actions;
    private float cubeRotation;

    public CubeScene(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    @Override
    public void show() {
        meshPass = new MeshRenderCoordinator();
        camera = new PerspectiveCamera3D();
        var viewport = graphicsDevice.getViewport();
        camera.resize(viewport.width, viewport.height);
        camera.setPosition(0f, 0f, 3f);

        cubeMesh = CubeMesh.createColoredUnitCube();
        shader = Shader.create("vulkan/3d/vert.glsl", "vulkan/3d/frag.glsl");

        actions = new InputActionMap("CubeScene");
        var moveAction = actions.addAction("Move", ActionType.VECTOR2);
        moveAction.addCompositeBinding().up(Keys.W).down(Keys.S).left(Keys.A).right(Keys.D);
        moveAction.addStickCompositeBinding(GamepadAxis.LEFT_X, GamepadAxis.LEFT_Y);

        var lookAction = actions.addAction("Look", ActionType.VECTOR2);
        lookAction.addStickCompositeBinding(GamepadAxis.RIGHT_X, GamepadAxis.RIGHT_Y);

        var riseAction = actions.addAction("Rise").addBinding(Keys.SPACE).addBinding(GamepadButton.RIGHT_BUMPER);
        var descendAction =
                actions.addAction("Descend").addBinding(Keys.LEFT_CTRL).addBinding(GamepadButton.LEFT_BUMPER);
        var sprintAction =
                actions.addAction("Sprint").addBinding(Keys.LEFT_SHIFT).addBinding(GamepadButton.LEFT_THUMB);

        var toggleMouseCaptureAction = actions.addAction("ToggleMouseCapture").addBinding(Keys.TAB);
        actions.addAction("Exit")
                .addBinding(Keys.ESCAPE)
                .addBinding(GamepadButton.START)
                .onPerformed(ctx -> game.exit());
        actions.enable();

        cameraController = new FreeCameraController3D(
                camera, moveAction, lookAction, riseAction, descendAction, toggleMouseCaptureAction, sprintAction);
        cameraController.setMouseCaptureEnabled(true);
    }

    @Override
    public void update(GameTime gameTime) {
        cameraController.update(gameTime);
        cubeRotation += gameTime.elapsedSeconds() * 0.75f;
    }

    @Override
    public void draw(GameTime gameTime) {
        if (!graphicsDevice.beginFrame(Color.BLACK)) {
            return;
        }

        modelMatrix.identity().rotateY(cubeRotation).rotateX(cubeRotation * 0.5f);
        shader.setUniform(ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName, camera.getProjectionMatrix());
        shader.setUniform(ShaderProps.UNIFORM_VIEW_MATRIX.glslName, camera.getViewMatrix());
        shader.setUniform(ShaderProps.UNIFORM_MODEL_MATRIX.glslName, modelMatrix);

        meshPass.begin();
        meshPass.draw(cubeMesh, shader);
        meshPass.end();
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.resize(width, height);
        }
    }

    @Override
    public void hide() {
        Window.setCursorMode(CursorMode.NORMAL);
    }

    @Override
    public void dispose() {
        Window.setCursorMode(CursorMode.NORMAL);
        if (actions != null) {
            actions.disable();
        }
        if (cubeMesh != null) {
            cubeMesh.dispose();
        }
        if (shader != null) {
            shader.dispose();
        }
    }
}
