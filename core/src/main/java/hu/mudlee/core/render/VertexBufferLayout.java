package hu.mudlee.core.render;

import java.util.Arrays;

public final class VertexBufferLayout {
    private final VertexInputRate inputRate;
    private final VertexLayoutAttribute[] attributes;

    public VertexBufferLayout(VertexLayoutAttribute... attributes) {
        this(VertexInputRate.PER_VERTEX, attributes);
    }

    public VertexBufferLayout(VertexInputRate inputRate, VertexLayoutAttribute... attributes) {
        this.inputRate = inputRate;
        this.attributes = attributes.clone();
    }

    public VertexInputRate inputRate() {
        return inputRate;
    }

    public VertexLayoutAttribute[] attributes() {
        return attributes.clone();
    }

    public int stride() {
        return attributes.length > 0 ? attributes[0].getStride() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VertexBufferLayout other)) {
            return false;
        }
        return inputRate == other.inputRate && Arrays.equals(attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        return 31 * inputRate.hashCode() + Arrays.hashCode(attributes);
    }

    @Override
    public String toString() {
        return "VertexBufferLayout{inputRate=" + inputRate + ", attributes=" + Arrays.toString(attributes) + "}";
    }
}
