package hu.mudlee.core.ecs.component;

import hu.mudlee.core.ecs.Component;
import hu.mudlee.core.ecs.Entity;
import org.joml.Vector2f;

public final class Transform2DComponent implements Component {

    public final Vector2f position = new Vector2f();
    public float rotation = 0f;
    public final Vector2f scale = new Vector2f(1f, 1f);
    public int z = 0;

    private Entity parent;
    private final Vector2f worldPosition = new Vector2f();
    private float worldRotation = 0f;
    private final Vector2f worldScale = new Vector2f(1f, 1f);

    public Entity getParent() {
        return parent;
    }

    public void setParent(Entity parent) {
        this.parent = parent;
    }

    public Vector2f getWorldPosition() {
        return worldPosition;
    }

    public float getWorldRotation() {
        return worldRotation;
    }

    public Vector2f getWorldScale() {
        return worldScale;
    }

    /**
     * Computes world-space transform from a parent's world transform and this entity's local
     * transform. Called by the transform propagation system.
     */
    public void propagateFrom(Transform2DComponent parentTransform) {
        var cos = (float) Math.cos(parentTransform.worldRotation);
        var sin = (float) Math.sin(parentTransform.worldRotation);

        var localX = position.x * parentTransform.worldScale.x;
        var localY = position.y * parentTransform.worldScale.y;

        worldPosition.set(
                parentTransform.worldPosition.x + cos * localX - sin * localY,
                parentTransform.worldPosition.y + sin * localX + cos * localY);
        worldRotation = parentTransform.worldRotation + rotation;
        worldScale.set(parentTransform.worldScale.x * scale.x, parentTransform.worldScale.y * scale.y);
    }

    /** Copies local transform to world transform (root entity with no parent). */
    public void propagateRoot() {
        worldPosition.set(position);
        worldRotation = rotation;
        worldScale.set(scale);
    }
}
