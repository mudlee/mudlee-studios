package hu.mudlee.core.content;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.texture.Texture2D;
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
    }
}
