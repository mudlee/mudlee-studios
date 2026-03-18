package hu.mudlee.core.render;

public record RenderPassOptions(ColorLoadAction colorLoadAction) {

    public static RenderPassOptions clearColor() {
        return new RenderPassOptions(ColorLoadAction.CLEAR);
    }

    public static RenderPassOptions loadColor() {
        return new RenderPassOptions(ColorLoadAction.LOAD);
    }
}
