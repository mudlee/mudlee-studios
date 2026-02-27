package hu.mudlee.sandbox;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.SpriteBatch2D;
import hu.mudlee.core.render.animation.Animation2D;
import hu.mudlee.core.render.animation.AnimationPlayer2D;
import hu.mudlee.core.render.animation.PlayMode;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera2D;
import hu.mudlee.core.render.texture.SpriteSheet2D;
import hu.mudlee.core.render.texture.Texture2D;
import org.joml.Vector2f;

public class PlayerScreen implements Screen {

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

    private static final float SCALE = 8f;
    private static final float MOVE_SPEED = 300f;

    private final Game game;
    private final GraphicsDevice graphicsDevice;

    private ContentManager content;
    private SpriteBatch2D SpriteBatch2D;
    private Camera2D camera;
    private AnimationPlayer2D playerAnimation;
    private InputActionMap actions;

    // All animations from the sprite sheet
    private Animation2D idleDown, idleRight, idleUp;
    private Animation2D walkDown, walkRight, walkUp;
    private Animation2D attackDown, attackRight, attackUp;
    private Animation2D die;

    private Direction direction = Direction.RIGHT;
    private State state = State.IDLE;
    private final Vector2f position = new Vector2f(960, 540);
    private final Vector2f origin = new Vector2f();

    public PlayerScreen(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    @Override
    public void show() {
        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet2D(texture, 48, 48);

        idleDown = sheet.createAnimation("IdleDown", 0, 0, 6, 0.12f, PlayMode.LOOP);
        idleRight = sheet.createAnimation("IdleRight", 1, 0, 6, 0.12f, PlayMode.LOOP);
        idleUp = sheet.createAnimation("IdleUp", 2, 0, 6, 0.12f, PlayMode.LOOP);

        walkDown = sheet.createAnimation("WalkDown", 3, 0, 6, 0.08f, PlayMode.LOOP);
        walkRight = sheet.createAnimation("WalkRight", 4, 0, 6, 0.08f, PlayMode.LOOP);
        walkUp = sheet.createAnimation("WalkUp", 5, 0, 6, 0.08f, PlayMode.LOOP);

        attackDown = sheet.createAnimation("AttackDown", 6, 0, 4, 0.10f, PlayMode.ONCE);
        attackRight = sheet.createAnimation("AttackRight", 7, 0, 4, 0.10f, PlayMode.ONCE);
        attackUp = sheet.createAnimation("AttackUp", 8, 0, 4, 0.10f, PlayMode.ONCE);

        die = sheet.createAnimation("Die", 9, 0, 3, 0.20f, PlayMode.ONCE);

        playerAnimation = new AnimationPlayer2D();
        playerAnimation.play(idleRight);

        SpriteBatch2D = new SpriteBatch2D();
        camera = new OrthographicCamera2D();

        actions = new InputActionMap("Player");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> game.exit());
        actions.enable();

        camera.position.set(position);
    }

    @Override
    public void update(GameTime gameTime) {
        var ks = Keyboard.getState();
        var dt = gameTime.elapsedSeconds();

        // TODO: state machine
        if (state == State.DIE) {
            playerAnimation.update(gameTime);
            return;
        } else if (state == State.ATTACK) {
            playerAnimation.update(gameTime);
            if (playerAnimation.isFinished()) {
                state = State.IDLE;
            }
            return;
        } else if (ks.isKeyDown(Keys.X)) {
            state = State.DIE;
            playerAnimation.play(die);
            playerAnimation.update(gameTime);
        } else if (ks.isKeyDown(Keys.SPACE)) {
            state = State.ATTACK;
            playerAnimation.play(attackFor(direction));
            playerAnimation.update(gameTime);
            return;
        }

        // Movement
        var moving = false;
        if (ks.isKeyDown(Keys.RIGHT)) {
            position.x += MOVE_SPEED * dt;
            direction = Direction.RIGHT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.LEFT)) {
            position.x -= MOVE_SPEED * dt;
            direction = Direction.LEFT;
            moving = true;
        }
        if (ks.isKeyDown(Keys.DOWN)) {
            position.y -= MOVE_SPEED * dt;
            direction = Direction.DOWN;
            moving = true;
        }
        if (ks.isKeyDown(Keys.UP)) {
            position.y += MOVE_SPEED * dt;
            direction = Direction.UP;
            moving = true;
        }

        state = moving ? State.WALK : State.IDLE;
        playerAnimation.play(animationFor(state, direction));
        playerAnimation.update(gameTime);
        // camera.position.set(position);
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        SpriteBatch2D.begin(camera.getTransformMatrix());
        SpriteBatch2D.draw(
                playerAnimation.getCurrentFrame(),
                position,
                Color.WHITE,
                0f,
                origin,
                SCALE,
                direction == Direction.LEFT,
                false);
        SpriteBatch2D.end();
    }

    @Override
    public void dispose() {
        actions.disable();
        SpriteBatch2D.dispose();
        content.unload();
    }

    private Animation2D animationFor(State s, Direction d) {
        return switch (s) {
            case IDLE ->
                switch (d) {
                    case DOWN -> idleDown;
                    case UP -> idleUp;
                    default -> idleRight;
                };
            case WALK ->
                switch (d) {
                    case DOWN -> walkDown;
                    case UP -> walkUp;
                    default -> walkRight;
                };
            case ATTACK -> attackFor(d);
            case DIE -> die;
        };
    }

    private Animation2D attackFor(Direction d) {
        return switch (d) {
            case DOWN -> attackDown;
            case UP -> attackUp;
            default -> attackRight;
        };
    }
}
