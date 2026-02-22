package hu.mudlee.sandbox.scenes;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import hu.mudlee.core.Application;
import hu.mudlee.core.ecs.ECS;
import hu.mudlee.core.ecs.entities.RawRenderableEntity;
import hu.mudlee.core.input.KeyListener;
import hu.mudlee.core.render.*;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.types.*;
import hu.mudlee.core.scene.Scene;
import hu.mudlee.core.scene.SceneManager;
import org.joml.Vector4f;

public class GameScene implements Scene {
  private Shader shader;
  private VertexArray va;
  private Camera2D camera;
  private Texture2D texture;

  @Override
  public void start() {
    System.out.println("GameScene HELLO");
    Renderer.setClearColor(new Vector4f(0, 0, 0, 1));

    texture = Texture2D.create("/textures/mario.png");

    camera = new Camera2D();
    camera.position.x -= 100;
    camera.position.y -= 100;
    texture.bind();

    shader = Shader.create("vulkan/2d/vert.glsl", "vulkan/2d/frag.glsl");
    shader.createUniform(
        shader.getVertexProgramId(), ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName);
    shader.setUniform(
        shader.getVertexProgramId(),
        ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName,
        camera.getProjectionMatrix());

    shader.createUniform(shader.getVertexProgramId(), ShaderProps.UNIFORM_VIEW_MATRIX.glslName);
    shader.setUniform(
        shader.getVertexProgramId(),
        ShaderProps.UNIFORM_VIEW_MATRIX.glslName,
        camera.getViewMatrix());

    shader.createUniform(shader.getFragmentProgramId(), "TEX_SAMPLER");
    shader.setUniform(shader.getFragmentProgramId(), "TEX_SAMPLER", 0);

    var stride = (3 + 4 + 2) * Float.BYTES; // 3 for positions, 4 for colors
    final var layout =
        new VertexBufferLayout(
            new VertexLayoutAttribute(0, 3, ShaderTypes.FLOAT, false, stride, 0),
            new VertexLayoutAttribute(1, 4, ShaderTypes.FLOAT, false, stride, 3 * Float.BYTES),
            new VertexLayoutAttribute(
                2, 2, ShaderTypes.FLOAT, false, stride, 7 * Float.BYTES) /*,

				new VertexLayoutInstancedAttribute(2,4, ShaderTypes.FLOAT,false,16 * Float.BYTES,0,1),
				new VertexLayoutInstancedAttribute(3,4, ShaderTypes.FLOAT,false,16 * Float.BYTES,4 * Float.BYTES,1),
				new VertexLayoutInstancedAttribute(4,4, ShaderTypes.FLOAT,false,16 * Float.BYTES,8 * Float.BYTES,1),
				new VertexLayoutInstancedAttribute(5,4, ShaderTypes.FLOAT,false,16 * Float.BYTES,12 * Float.BYTES,1)*/);

    va = VertexArray.create();
    va.addVBO(VertexBuffer.create(squareVertices, layout, BufferUsage.STATIC_DRAW));
    va.setEBO(ElementBuffer.create(squareIndices, BufferUsage.STATIC_DRAW));

    ECS.addEntity(
        new RawRenderableEntity("Square", va, shader, RenderMode.TRIANGLES, PolygonMode.FILL));
  }

  @Override
  public void update(float deltaTime) {
    if (KeyListener.isKeyPressed(GLFW_KEY_ESCAPE)) {
      Application.stop();
    }

    if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
      System.out.println("?");
      SceneManager.setScreen(new OtherScene());
    }

    // if(camera.update()) {

    // }
    camera.position.x -= deltaTime * 50f;
    camera.position.y -= deltaTime * 20f;
    shader.setUniform(
        shader.getVertexProgramId(),
        ShaderProps.UNIFORM_PROJECTION_MATRIX.glslName,
        camera.getProjectionMatrix());
    shader.setUniform(
        shader.getVertexProgramId(),
        ShaderProps.UNIFORM_VIEW_MATRIX.glslName,
        camera.getViewMatrix());
  }

  @Override
  public void resize(int width, int height) {
    // camera.resize(width, height);
  }

  @Override
  public void dispose() {
    ECS.removeAllEntities();
    shader.dispose();
    va.dispose();
    texture.dispose();
  }

  private static final float[] squareVertices = {
    // pos | color | texture uv
    100.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1, 1, // bottom right
    0.5f, 100.5f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0, 0, // top left
    100.5f, 100.5f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1, 0, // top right
    0.5f, 0.5f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0, 1, // bottom left
  };

  private static final int[] squareIndices = {
    2, 1, 0,
    0, 1, 3
  };
}
