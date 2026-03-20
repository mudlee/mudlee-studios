package hu.mudlee.core.render.types;

public enum ShaderProps {
    UNIFORM_PROJECTION_MATRIX("uProjection"),
    UNIFORM_VIEW_MATRIX("uView"),
    UNIFORM_MODEL_MATRIX("uModel");

    public final String glslName;

    ShaderProps(String glslName) {
        this.glslName = glslName;
    }
}
