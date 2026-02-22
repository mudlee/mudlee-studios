package hu.mudlee.core.render.opengl;

import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.texture.TextureLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.stb.STBImage.stbi_image_free;

public class OpenGLTexture2D extends Texture2D {
	private final static Logger LOG = LoggerFactory.getLogger(OpenGLTexture2D.class);
	private final String path;
	private final int textureId;

	public OpenGLTexture2D(String path) {
		this.path = path;

		final var data = TextureLoader.loadFromResources(path);

		textureId = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureId);
		glTexImage2D(GL_TEXTURE_2D, 0, mapChannelsToColorFormat(data.channels()), data.width(), data.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, data.image());

		// Repeat texture in both directions
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		// When stretch, pixelate
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		// When shrinking, pixelate
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		// Free memory
		stbi_image_free(data.image());
		unBind();
	}

	@Override
	public void bind() {
		glActiveTexture(GL_TEXTURE0); // TODO: we should not use it here, and deactive somewhere else...
		glBindTexture(GL_TEXTURE_2D, textureId);
	}

	@Override
	public void unBind() {
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	@Override
	public void dispose() {
		glDeleteTextures(textureId);
	}

	private int mapChannelsToColorFormat(int channels) {
		switch (channels) {
			case 3:
				return GL_RGB;
			case 4:
				return GL_RGBA;
			default:
				LOG.error("Failed to create texture, not handled channels: {}", channels);
				throw new RuntimeException("Failed to generate texture");
		}
	}
}
