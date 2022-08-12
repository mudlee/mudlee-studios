package hu.mudlee.sandbox;

import hu.mudlee.core.Engine;
import hu.mudlee.core.LifeCycleListener;
import hu.mudlee.core.input.InputMultiplexer;
import hu.mudlee.core.input.InputProcessor;
import hu.mudlee.core.render.*;
import hu.mudlee.core.render.types.BufferDataLocation;
import hu.mudlee.core.render.types.PolygonMode;
import hu.mudlee.core.render.types.RenderMode;
import hu.mudlee.core.render.types.ShaderTypes;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public class SandboxGame implements LifeCycleListener, InputProcessor {
	private final InputMultiplexer inputMultiplexer = new InputMultiplexer();
	private Shader shader;
	private VertexArray va;

	@Override
	public void onCreated() {
		inputMultiplexer.addProcessor(this);
		Engine.input.setMultiplexer(inputMultiplexer);

		/*camera = new PerspectiveCamera(60f, 0.01f, 1000f);
		camera.resize(window.getSize().x, window.getSize().y);
		camera.setPosition(new Vector3f(0,0,5));

		shader = Shader.create("basic.vert", "basic.frag");
		shader.createUniform(shader.getVertexProgramId(), Shader.UNIFORM_PROJ_MAT);
		shader.createUniform(shader.getVertexProgramId(), Shader.UNIFORM_VIEW_MAT);
		shader.setUniform(shader.getVertexProgramId(), Shader.UNIFORM_PROJ_MAT, camera.getProjectionMatrix());
		shader.setUniform(shader.getVertexProgramId(), Shader.UNIFORM_VIEW_MAT, camera.getViewMatrix());*/

		shader = Shader.create("simple/basic_vert.glsl", "simple/basic_frag.glsl");
		//shader = Shader.create("simple/colored_vert.glsl", "simple/colored_frag.glsl");

		int stride = 7 * Float.BYTES;
		final var layout = new VertexBufferLayout(
				new VertexLayoutAttribute(0, 3, ShaderTypes.FLOAT, false, stride, 0),
				new VertexLayoutAttribute(1, 4, ShaderTypes.FLOAT, false, stride, 3 * Float.BYTES)
		);

		va = VertexArray.create();
		va.addVertexBuffer(VertexBuffer.create(squareColoredIndexed, layout, BufferDataLocation.STATIC_DRAW));
		va.setIndexBuffer(IndexBuffer.create(squareInd));
		va.setInstanceCount(3);
	}

	@Override
	public void onKeyPress(int keyCode) {
		if(keyCode == GLFW_KEY_ESCAPE) {
			Engine.app.stop();
		}
	}

	@Override
	public void onUpdate(float delta) {
		Engine.app.renderer.renderRaw(va, shader, RenderMode.TRIANGLES, PolygonMode.FILL);
	}

	private static final float[] squareColoredIndexed = {
			-0.5f, -0.5f, 0.0f, 0.2f, 0.5f, 0.5f, 1f,
			0.5f, -0.5f, 0.0f, 0.2f, 0.5f, 0.5f, 1f,
			0.5f, 0.5f, 0.0f, 0.2f, 0.5f, 0.5f, 1f,
			-0.5f, 0.5f, 0.0f, 0.2f, 0.5f, 0.5f, 1f,
	};

	private static final float[] squareBasic = {
			0.5f,  0.5f, 0.0f,
			-0.5f, -0.5f, 0.0f,
			0.5f, -0.5f, 0.0f,

			-0.5f, -0.5f, 0.0f,
			0.5f,  0.5f, 0.0f,
			-0.5f, 0.5f, 0.0f,
	};

	private static final int[] squareInd = {
			0, 1, 2, 2, 3, 0
	};
}
