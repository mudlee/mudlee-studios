package hu.mudlee.sandbox;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.Aspect;
import hu.mudlee.core.ecs.ComponentMapper;
import hu.mudlee.core.ecs.ComponentMapperService;
import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.EntityProcessingSystem;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.input.InputAction;
import hu.mudlee.sandbox.PlayerStateComponent.Direction;
import hu.mudlee.sandbox.PlayerStateComponent.State;

public final class PlayerControlSystem extends EntityProcessingSystem {

    private final InputAction moveAction;
    private final InputAction attackAction;
    private final InputAction dieAction;

    private ComponentMapper<PlayerStateComponent> psMapper;
    private ComponentMapper<Transform2DComponent> transformMapper;
    private ComponentMapper<Sprite2DComponent> spriteMapper;
    private ComponentMapper<Animation2DComponent> animMapper;

    public PlayerControlSystem(InputAction moveAction, InputAction attackAction, InputAction dieAction) {
        super(Aspect.all(
                PlayerStateComponent.class,
                Transform2DComponent.class,
                Sprite2DComponent.class,
                Animation2DComponent.class));
        this.moveAction = moveAction;
        this.attackAction = attackAction;
        this.dieAction = dieAction;
    }

    @Override
    public void initialize(ComponentMapperService mappers) {
        psMapper = mappers.getMapper(PlayerStateComponent.class);
        transformMapper = mappers.getMapper(Transform2DComponent.class);
        spriteMapper = mappers.getMapper(Sprite2DComponent.class);
        animMapper = mappers.getMapper(Animation2DComponent.class);
    }

    @Override
    protected void process(GameTime gameTime, Entity entity) {
        var ps = psMapper.get(entity);
        var t = transformMapper.get(entity);
        var s = spriteMapper.get(entity);
        var anim = animMapper.get(entity);
        var dt = gameTime.elapsedSeconds();

        if (ps.state == State.DIE) {
            return;
        }
        if (ps.state == State.ATTACK) {
            if (anim.player.isFinished()) {
                ps.state = State.IDLE;
            }
            return;
        }
        if (dieAction.isPressed()) {
            ps.state = State.DIE;
            anim.play("Die");
            return;
        }
        if (attackAction.isPressed()) {
            ps.state = State.ATTACK;
            anim.play(attackFor(ps.direction));
            return;
        }

        var vec = moveAction.readVector2();
        var moving = vec.x != 0 || vec.y != 0;
        if (vec.x > 0) {
            t.position.x += ps.moveSpeed * dt;
            ps.direction = Direction.RIGHT;
        }
        if (vec.x < 0) {
            t.position.x -= ps.moveSpeed * dt;
            ps.direction = Direction.LEFT;
        }
        if (vec.y < 0) {
            t.position.y -= ps.moveSpeed * dt;
            ps.direction = Direction.DOWN;
        }
        if (vec.y > 0) {
            t.position.y += ps.moveSpeed * dt;
            ps.direction = Direction.UP;
        }

        ps.state = moving ? State.WALK : State.IDLE;
        anim.play(animFor(ps.state, ps.direction));
        s.flipX = (ps.direction == Direction.LEFT);
    }

    private String animFor(State s, Direction d) {
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
            case ATTACK -> attackFor(d);
            case DIE -> "Die";
        };
    }

    private String attackFor(Direction d) {
        return switch (d) {
            case DOWN -> "AttackDown";
            case UP -> "AttackUp";
            default -> "AttackRight";
        };
    }
}
