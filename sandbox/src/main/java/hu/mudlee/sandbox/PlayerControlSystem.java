package hu.mudlee.sandbox;

import hu.mudlee.core.GameTime;
import hu.mudlee.core.ecs.EntityManager;
import hu.mudlee.core.ecs.SystemBase;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;
import hu.mudlee.sandbox.PlayerStateComponent.Direction;
import hu.mudlee.sandbox.PlayerStateComponent.State;

public final class PlayerControlSystem extends SystemBase {

    public PlayerControlSystem(EntityManager em) {
        super(em);
    }

    @Override
    public void update(GameTime gameTime) {
        for (var entity : em.getEntitiesWith(
                PlayerStateComponent.class,
                Transform2DComponent.class,
                Sprite2DComponent.class,
                Animation2DComponent.class)) {
            var ps = em.getComponent(entity, PlayerStateComponent.class);
            var t = em.getComponent(entity, Transform2DComponent.class);
            var s = em.getComponent(entity, Sprite2DComponent.class);
            var anim = em.getComponent(entity, Animation2DComponent.class);
            var ks = Keyboard.getState();
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
            if (ks.isKeyDown(Keys.X)) {
                ps.state = State.DIE;
                anim.play("Die");
                return;
            }
            if (ks.isKeyDown(Keys.SPACE)) {
                ps.state = State.ATTACK;
                anim.play(attackFor(ps.direction));
                return;
            }

            var moving = false;
            if (ks.isKeyDown(Keys.RIGHT)) {
                t.position.x += ps.moveSpeed * dt;
                ps.direction = Direction.RIGHT;
                moving = true;
            }
            if (ks.isKeyDown(Keys.LEFT)) {
                t.position.x -= ps.moveSpeed * dt;
                ps.direction = Direction.LEFT;
                moving = true;
            }
            if (ks.isKeyDown(Keys.DOWN)) {
                t.position.y -= ps.moveSpeed * dt;
                ps.direction = Direction.DOWN;
                moving = true;
            }
            if (ks.isKeyDown(Keys.UP)) {
                t.position.y += ps.moveSpeed * dt;
                ps.direction = Direction.UP;
                moving = true;
            }

            ps.state = moving ? State.WALK : State.IDLE;
            anim.play(animFor(ps.state, ps.direction));
            s.flipX = (ps.direction == Direction.LEFT);
        }
    }

    private String animFor(State s, Direction d) {
        return switch (s) {
            case IDLE -> switch (d) {
                case DOWN -> "IdleDown";
                case UP -> "IdleUp";
                default -> "IdleRight";
            };
            case WALK -> switch (d) {
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
