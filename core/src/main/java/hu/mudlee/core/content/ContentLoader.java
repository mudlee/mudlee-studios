package hu.mudlee.core.content;

@FunctionalInterface
public interface ContentLoader<T> {
    T load(ContentManager manager, String assetName);
}
