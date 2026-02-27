package hu.mudlee.sandbox;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.gameobject.Component;
import hu.mudlee.core.gameobject.components.Animator2D;
import hu.mudlee.core.gameobject.components.SpriteRenderer2D;
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
        var Animator2D = getComponent(Animator2D.class);
        var sr = getComponent(SpriteRenderer2D.class);

        if (state == State.DIE) {
            return;
        }
        if (state == State.ATTACK) {
            if (Animator2D.isFinished()) {
                state = State.IDLE;
            }
            return;
        }
        if (ks.isKeyDown(Keys.X)) {
            state = State.DIE;
            Animator2D.play("Die");
            return;
        }
        if (ks.isKeyDown(Keys.SPACE)) {
            state = State.ATTACK;
            Animator2D.play(attackAnimationFor(direction));
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
        Animator2D.play(animationFor(state, direction));
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
