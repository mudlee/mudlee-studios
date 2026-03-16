package hu.mudlee.core.render;

import hu.mudlee.core.render.types.ShaderTypes;

public class VertexLayoutAttribute {
    private final int index;
    private final int dataSize;
    private final ShaderTypes dataType;
    private final int stride;
    private final int offset;
    private final boolean normalized;

    public VertexLayoutAttribute(
            int index, int dataSize, ShaderTypes dataType, boolean normalized, int stride, int offset) {
        this.index = index;
        this.dataSize = dataSize;
        this.dataType = dataType;
        this.normalized = normalized;
        this.stride = stride;
        this.offset = offset;
    }

    public int getIndex() {
        return index;
    }

    public int getDataSize() {
        return dataSize;
    }

    public ShaderTypes getDataType() {
        return dataType;
    }

    public boolean isNormalized() {
        return normalized;
    }

    public int getStride() {
        return stride;
    }

    public int getOffset() {
        return offset;
    }
}
