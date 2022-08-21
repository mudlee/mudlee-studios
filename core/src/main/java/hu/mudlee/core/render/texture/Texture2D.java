package hu.mudlee.core.render.texture;

import hu.mudlee.core.render.opengl.OpenGLTexture2D;

public abstract class Texture2D {
	public static Texture2D create(String path) {
		return new OpenGLTexture2D(path);
	}

	public abstract void bind();

	public abstract void unBind();
}
