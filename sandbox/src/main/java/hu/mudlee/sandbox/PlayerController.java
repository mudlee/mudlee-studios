package hu.mudlee.sandbox;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.gameobject.Component;
import hu.mudlee.core.gameobject.components.Animator;
import hu.mudlee.core.gameobject.components.SpriteRenderer;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;

public class PlayerController extends Component {

    private final float moveSpeed;
    private Direction direction = Direction.RIGHT;
    private State state = State.IDLE;

    public PlayerController(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    @Override
    public void update(GameTime gameTime) {
        var ks = Keyboard.getState();
        var dt = gameTime.elapsedSeconds();
        var transform = getGameObject().transform;
        var animator = getComponent(Animator.class);
        var sr = getComponent(SpriteRenderer.class);

        if (state == State.DIE) {
            return;
        }
        if (state == State.ATTACK) {
            if (animator.isFinished()) {
                state = State.IDLE;
            }
            return;
        }
        if (ks.isKeyDown(Keys.X)) {
            state = State.DIE;
            animator.play("Die");
            return;
        }
        if (ks.isKeyDown(Keys.SPACE)) {
            state = State.ATTACK;
            animator.play(attackAnimationFor(direction));
            return;
        }

        var moving = false;
        if (ks.isKeyDown(Keys.RIGHT)) {
            transform.position.x += moveSpeed * dt;
            direction = Direction.RIGHT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.LEFT)) {
            transform.position.x -= moveSpeed * dt;
            direction = Direction.LEFT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.DOWN)) {
            transform.position.y -= moveSpeed * dt;
            direction = Direction.DOWN;
            moving = true;
        }
        if (ks.isKeyDown(Keys.UP)) {
            transform.position.y += moveSpeed * dt;
            direction = Direction.UP;
            moving = true;
        }

        state = moving ? State.WALK : State.IDLE;
        animator.play(animationFor(state, direction));
        sr.flipX = (direction == Direction.LEFT);
    }

    private String animationFor(State s, Direction d) {
        return switch (s) {
            case IDLE ->
                switch (d) {
                    case DOWN -> "IdleDown";
                    case UP -> "IdleUp";
                    default -> "IdleRight";
                };
            case WALK ->
                switch (d) {
                    case DOWN -> "WalkDown";
                    case UP -> "WalkUp";
                    default -> "WalkRight";
                };
            case ATTACK -> attackAnimationFor(d);
            case DIE -> "Die";
        };
    }

    private String attackAnimationFor(Direction d) {
        return switch (d) {
            case DOWN -> "AttackDown";
            case UP -> "AttackUp";
            default -> "AttackRight";
        };
    }

    private enum Direction {
        DOWN,
        RIGHT,
        UP,
        LEFT
    }

    private enum State {
        IDLE,
        WALK,
        ATTACK,
        DIE
    }
}
