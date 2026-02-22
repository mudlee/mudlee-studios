package hu.mudlee.sandbox;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.entities.RawRenderableEntity;
import hu.mudlee.core.input.KeyListener;
import hu.mudlee.core.render.ElementBuffer;
import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexArray;
import hu.mudlee.core.render.VertexBuffer;
import hu.mudlee.core.render.VertexBufferLayout;
import hu.mudlee.core.render.VertexLayoutAttribute;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.types.BufferUsage;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import hu.mudlee.core.render.types.ShaderProps;
import hu.mudlee.core.render.types.ShaderTypes;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;
import org.joml.Vector4f;

public class SandboxApplication extends Game {

    private Shader shader;
    private VertexArray va;
    private Camera2D camera;
    private Texture2D texture;

    private static final float[] SQUARE_VERTICES = {
        // pos | color | texture uv
        100.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1, 1, // bottom right
        0.5f, 100.5f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0, 0, // top left
        100.5f, 100.5f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1, 0, // top right
        0.5f, 0.5f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0, 1, // bottom left
    };

    private static final int[] SQUARE_INDICES = {
        2, 1, 0,
        0, 1, 3
    };

    public SandboxApplication() {
        super(WindowPreferences.builder()
                .title("TESTING")
                .antialiasing(Antialiasing.OFF)
                .fullscreen(false)
                .vSync(true)
                .width(1920)
                .height(1080)
                .build());
    }

    @Override
    protected void loadContent() {
        Renderer.setClearColor(new Vector4f(0, 0, 0, 1));

        texture = Texture2D.create("/textures/mario.png");

        camera = new Camera2D();
        camera.position.x -= 100;
        camera.position.y -= 100;
        texture.bind();

        shader = Shader.create("vulkan/2d/vert.glsl", "vulkan/2d/frag.glsl");
        shader.createUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName);
        shader.setUniform(
                shader.getVertexProgramId(),
                ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName,
                camera.getProjectionMatrix());

        shader.createUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName);
        shader.setUniform(
                shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName, camera.getViewMatrix());

        shader.createUniform(shader.getFragmentProgramId(), "TEX_SAMPLER");
        shader.setUniform(shader.getFragmentProgramId(), "TEX_SAMPLER", 0);

        var stride = (3 + 4 + 2) * Float.BYTES;
        var layout = new VertexBufferLayout(
                new VertexLayoutAttribute(0, 3, ShaderTypes.FLOAT, false, stride, 0),
                new VertexLayoutAttribute(1, 4, ShaderTypes.FLOAT, false, stride, 3 * Float.BYTES),
                new VertexLayoutAttribute(2, 2, ShaderTypes.FLOAT, false, stride, 7 * Float.BYTES));

        va = VertexArray.create();
        va.addVBO(VertexBuffer.create(SQUARE_VERTICES, layout, BufferUsage.STATIC_DRAW));
        va.setEBO(ElementBuffer.create(SQUARE_INDICES, BufferUsage.STATIC_DRAW));

        ECS.addEntity(new RawRenderableEntity("Square", va, shader, RenderMode.TRIANGLES, PolygonMode.FILL));
    }

    @Override
    protected void update(GameTime gameTime) {
        if (KeyListener.isKeyPressed(GLFW_KEY_ESCAPE)) {
            exit();
        }

        camera.position.x -= gameTime.elapsedSeconds() * 50f;
        camera.position.y -= gameTime.elapsedSeconds() * 20f;
        shader.setUniform(
                shader.getVertexProgramId(),
                ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName,
                camera.getProjectionMatrix());
        shader.setUniform(
                shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName, camera.getViewMatrix());
    }

    @Override
    protected void unloadContent() {
        ECS.removeAllEntities();
        shader.dispose();
        va.dispose();
        texture.dispose();
    }

    public static void main(String[] args) {
        Renderer.configure(RenderBackend.VULKAN);
        new SandboxApplication().run();
    }
}
