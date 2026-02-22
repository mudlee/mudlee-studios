package hu.mudlee.core.render.texture;

import static org.lwjgl.stb.STBImage.stbi_failure_reason;

import hu.mudlee.core.io.ResourceLoader;
import java.nio.ByteBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureLoader {
  private static final Logger LOG = LoggerFactory.getLogger(TextureLoader.class);

  private TextureLoader() {}

  public static TextureData loadFromResources(String filePath) {
    LOG.debug("Loading Texture {}...", filePath);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      return loadFromByteBuffer(ResourceLoader.loadToByteBuffer(filePath, stack), stack, filePath);
    }
  }

  private static TextureData loadFromByteBuffer(
      ByteBuffer byteBuffer, MemoryStack stack, String path) {
    LOG.debug("Loading Texture from ByteBuffer...");
    final var w = stack.mallocInt(1);
    final var h = stack.mallocInt(1);
    final var channelsInFile = stack.mallocInt(1);

    final var image = STBImage.stbi_load_from_memory(byteBuffer, w, h, channelsInFile, 0);
    if (image == null) {
      LOG.error("Failed to load texture from ByteBuffer, reason: {}", stbi_failure_reason());
      throw new RuntimeException("Failed to load texture from ByteBuffer");
    }

    final var width = w.get();
    final var height = h.get();
    final var channels = channelsInFile.get();

    LOG.debug("Texture '{}' loaded, {}x{}, channels: {}", path, width, height, channels);

    return new TextureData(width, height, image, channels);
  }
}
