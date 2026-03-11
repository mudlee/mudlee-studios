package hu.mudlee.core.ui;

/**
 * Holds the screen-space position and size of a {@link UIObject}.
 *
 * <p>Used as a hint to backends; immediate-mode renderers like Dear ImGui may use {@code x}/{@code
 * y} to position floating windows via {@code ImGui.setNextWindowPos}.
 */
public final class UITransform {

    public float x;
    public float y;
    public float width;
    public float height;
}
