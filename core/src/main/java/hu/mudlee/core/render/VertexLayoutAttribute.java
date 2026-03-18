package hu.mudlee.core.render;

import hu.mudlee.core.render.types.ShaderTypes;
import java.util.Objects;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VertexLayoutAttribute other)) {
            return false;
        }
        return index == other.index
                && dataSize == other.dataSize
                && stride == other.stride
                && offset == other.offset
                && normalized == other.normalized
                && dataType == other.dataType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, dataSize, dataType, stride, offset, normalized);
    }
}
