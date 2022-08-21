package hu.mudlee.core.render.camera;

import hu.mudlee.core.window.Window;
import org.joml.Matrix4f;

public class OrthoCamera extends AbstractCamera {
	private float size = 1;
	private float zNear = 0;
	private float zFar = 1000;
	private float aspect;

	public OrthoCamera() {
	}

	public OrthoCamera(float size, float zNear, float zFar) {
		this.size = size;
		this.zNear = zNear;
		this.zFar = zFar;
	}

	@Override
	protected void updateProjectionMatrix(Matrix4f projectionMatrix) {
		aspect = (float) Window.getSize().x / (float)Window.getSize().y;
		projectionMatrix.setOrtho(-this.size * aspect, this.size * aspect, -this.size, this.size, this.zNear, this.zFar);
	}
}
