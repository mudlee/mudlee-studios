package hu.mudlee.core.render;

public class VertexLayoutAttribute {
    private final int index;
    private final int dataSize;
    private final int dataType;
    private final int stride;
    private final int offset;
    private final boolean normalized;

    public VertexLayoutAttribute(int index, int dataSize, int dataType, boolean normalized, int stride, int offset) {
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

    public int getDataType() {
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
