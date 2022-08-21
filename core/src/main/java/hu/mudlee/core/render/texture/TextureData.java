package hu.mudlee.core.render.texture;

import java.nio.ByteBuffer;

public record TextureData(int width, int height, ByteBuffer image, int channels) {
}
