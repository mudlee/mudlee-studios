package hu.mudlee.sandbox;

import hu.mudlee.core.ecs.Component;

public final class PlayerStateComponent implements Component {

    public Direction direction = Direction.RIGHT;
    public State state = State.IDLE;
    public float moveSpeed;

    public PlayerStateComponent(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public enum Direction {
        DOWN,
        RIGHT,
        UP,
        LEFT
    }

    public enum State {
        IDLE,
        WALK,
        ATTACK,
        DIE
    }
}
