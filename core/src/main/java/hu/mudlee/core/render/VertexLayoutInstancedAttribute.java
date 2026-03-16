package hu.mudlee.core.render;

import hu.mudlee.core.render.types.ShaderTypes;

public class VertexLayoutInstancedAttribute extends VertexLayoutAttribute {
    private final int divisor;

    public VertexLayoutInstancedAttribute(
            int position, int dataSize, ShaderTypes dataType, boolean normalized, int stride, int offset, int divisor) {
        super(position, dataSize, dataType, normalized, stride, offset);
        this.divisor = divisor;
    }

    public int getDivisor() {
        return divisor;
    }
}
