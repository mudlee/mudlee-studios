package hu.mudlee.core.content;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.texture.TextureAtlas;
import java.util.HashMap;
import java.util.Map;

public class ContentManager {

    private final String rootDirectory;
    private final Map<String, Object> cache = new HashMap<>();
    private final Map<Class<?>, ContentLoader<?>> loaders = new HashMap<>();

    public ContentManager(String rootDirectory) {
        this.rootDirectory = rootDirectory;
        registerDefaultLoaders();
    }

    public <T> void registerLoader(Class<T> type, ContentLoader<T> loader) {
        loaders.put(type, loader);
    }

    @SuppressWarnings("unchecked")
    public <T> T load(Class<T> type, String assetName) {
        var key = type.getName() + ":" + assetName;
        var cached = cache.get(key);
        if (cached != null) {
            return (T) cached;
        }
        var asset = resolve(type, assetName);
        cache.put(key, asset);
        return asset;
    }

    public void unload() {
        for (var asset : cache.values()) {
            if (asset instanceof Disposable d) {
                d.dispose();
            }
        }
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T resolve(Class<T> type, String assetName) {
        var loader = (ContentLoader<T>) loaders.get(type);
        if (loader == null) {
            throw new IllegalArgumentException("No loader registered for type: " + type.getName());
        }
        return loader.load(this, assetName);
    }

    String buildPath(String assetName, String extension) {
        if (rootDirectory == null || rootDirectory.isEmpty()) {
            return "/" + assetName + extension;
        }
        return "/" + rootDirectory + "/" + assetName + extension;
    }

    private void registerDefaultLoaders() {
        registerLoader(Texture2D.class, (manager, name) -> Texture2D.create(manager.buildPath(name, ".png")));
        registerLoader(TextureAtlas.class, (manager, name) -> {
            var manifest = ResourceLoader.load(manager.buildPath(name, ".atlas"));
            var builder = new TextureAtlas.Builder();
            for (var line : manifest.lines().toList()) {
                var trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                var eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                builder.add(
                        trimmed.substring(0, eq).trim(),
                        trimmed.substring(eq + 1).trim());
            }
            return builder.build();
        });
    }
}
