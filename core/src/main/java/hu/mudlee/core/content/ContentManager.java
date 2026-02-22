package hu.mudlee.core.content;

import hu.mudlee.core.Disposable;
import hu.mudlee.core.render.texture.Texture2D;
import java.util.HashMap;
import java.util.Map;

public class ContentManager {

    private final String rootDirectory;
    private final Map<String, Object> cache = new HashMap<>();

    public ContentManager(String rootDirectory) {
        this.rootDirectory = rootDirectory;
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

    private <T> T resolve(Class<T> type, String assetName) {
        if (type == Texture2D.class) {
            return type.cast(Texture2D.create(buildPath(assetName, ".png")));
        }
        throw new IllegalArgumentException("Unsupported content type: " + type.getName());
    }

    private String buildPath(String assetName, String extension) {
        if (rootDirectory == null || rootDirectory.isEmpty()) {
            return "/" + assetName + extension;
        }
        return "/" + rootDirectory + "/" + assetName + extension;
    }
}
