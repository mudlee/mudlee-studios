package hu.mudlee.core.render.types;

public record ShaderConfig(
        boolean depthTestEnabled,
        boolean depthWriteEnabled,
        boolean blendingEnabled,
        boolean usesModelMatrix,
        ShaderCullMode cullMode) {

    private static final String SHADER_PATH_3D_SEGMENT = "/3d/";

    public static ShaderConfig default2D() {
        return new ShaderConfig(false, false, true, false, ShaderCullMode.NONE);
    }

    public static ShaderConfig default3D() {
        return new ShaderConfig(true, true, false, true, ShaderCullMode.BACK);
    }

    public static ShaderConfig inferFromPaths(String vertexShaderName, String fragmentShaderName) {
        if (vertexShaderName.contains(SHADER_PATH_3D_SEGMENT) || fragmentShaderName.contains(SHADER_PATH_3D_SEGMENT)) {
            return default3D();
        }
        return default2D();
    }
}
