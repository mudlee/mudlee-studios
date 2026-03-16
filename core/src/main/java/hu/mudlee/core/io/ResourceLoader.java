package hu.mudlee.core.io;

import static org.lwjgl.system.MemoryUtil.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceLoader {
    private static final Logger log = LoggerFactory.getLogger(ResourceLoader.class);

    /**
     * Loads a resource into a native (direct) ByteBuffer allocated with {@code memAlloc}. The
     * caller is responsible for freeing it with {@code memFree}.
     */
    public static ByteBuffer loadToDirectByteBuffer(String path) {
        log.debug("Loading resource {}", path);
        try {
            final var url = ResourceLoader.class.getResource(path);
            if (url == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            final var resourceSize = url.openConnection().getContentLength();
            log.debug("Loading resource '{}' ({}bytes)", url.getFile(), resourceSize);
            final var buffer = memAlloc(resourceSize);
            try (var bis = new BufferedInputStream(url.openStream())) {
                int b;
                do {
                    b = bis.read();
                    if (b != -1) {
                        buffer.put((byte) b);
                    }
                } while (b != -1);
            }
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ByteBuffer loadToByteBuffer(String path, MemoryStack stack) {
        log.debug("Loading resource {}", path);
        try {
            final var url = ResourceLoader.class.getResource(path);

            if (url == null) {
                throw new RuntimeException("Resource not found: " + path);
            }

            final var resourceSize = url.openConnection().getContentLength();

            log.debug("Loading resource '{}' ({}bytes)", url.getFile(), resourceSize);

            final var resource = stack.calloc(resourceSize);

            try (BufferedInputStream bis = new BufferedInputStream(url.openStream())) {
                int b;
                do {
                    b = bis.read();
                    if (b != -1) {
                        resource.put((byte) b);
                    }
                } while (b != -1);
            }

            resource.flip();

            return resource;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String load(String path) {
        log.debug("Loading resource {}", path);
        var in = ResourceLoader.class.getResourceAsStream(path);
        if (in == null) {
            throw new RuntimeException("Could not find resource: " + path);
        }
        try (var scanner = new Scanner(in, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }
}
