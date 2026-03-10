package hu.mudlee.core.render.opengl;

import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.stb.STBImage.stbi_image_free;

import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.texture.TextureLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenGLTexture2D extends Texture2D {
    private static final Logger LOG = LoggerFactory.getLogger(OpenGLTexture2D.class);
    private final String path;
    private final int textureId;
    private final int width;
    private final int height;

    public OpenGLTexture2D(String path) {
        this.path = path;

        final var data = TextureLoader.loadFromResources(path);
        this.width = data.width();
        this.height = data.height();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                mapChannelsToColorFormat(data.channels()),
                data.width(),
                data.height(),
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                data.image());

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
        Renderer.incrementTextureCount();
    }

    /** Creates a texture directly from raw RGBA8 pixel data (e.g. font atlas or procedural textures). */
    public OpenGLTexture2D(java.nio.ByteBuffer pixels, int width, int height) {
        this.path = null;
        this.width = width;
        this.height = height;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        unBind();
        Renderer.incrementTextureCount();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getNativeHandle() {
        return textureId;
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
        Renderer.decrementTextureCount();
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
