package hu.mudlee.sandbox;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.ecs.Entity;
import hu.mudlee.core.ecs.World;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.CameraComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.ecs.system.Animation2DSystem;
import hu.mudlee.core.ecs.system.SpriteRender2DSystem;
import hu.mudlee.core.input.ActionType;
import hu.mudlee.core.input.GamepadAxis;
import hu.mudlee.core.input.GamepadButton;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.SpriteBatch2D;
import hu.mudlee.core.render.animation.PlayMode;
import hu.mudlee.core.render.camera.OrthographicCamera2D;
import hu.mudlee.core.render.texture.SpriteSheet2D;
import hu.mudlee.core.render.texture.Texture2D;

public class PlayerScene implements Screen {

    private final Game game;
    private final GraphicsDevice graphicsDevice;
    private final World world = new World();

    private SpriteBatch2D spriteBatch;
    private Entity cameraEntity;
    private ContentManager content;
    private InputActionMap actions;

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    @Override
    public void show() {
        spriteBatch = new SpriteBatch2D();
        cameraEntity = world.entities.createEntity();
        world.entities.addComponent(cameraEntity, new CameraComponent(new OrthographicCamera2D()));
        world.addSystem(new Animation2DSystem());
        world.addSystem(new SpriteRender2DSystem());

        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet2D(texture, 48, 48);

        var anim = new Animation2DComponent()
                .add("IdleDown", sheet.createAnimation("IdleDown", 0, 0, 6, 0.12f, PlayMode.LOOP))
                .add("IdleRight", sheet.createAnimation("IdleRight", 1, 0, 6, 0.12f, PlayMode.LOOP))
                .add("IdleUp", sheet.createAnimation("IdleUp", 2, 0, 6, 0.12f, PlayMode.LOOP))
                .add("WalkDown", sheet.createAnimation("WalkDown", 3, 0, 6, 0.08f, PlayMode.LOOP))
                .add("WalkRight", sheet.createAnimation("WalkRight", 4, 0, 6, 0.08f, PlayMode.LOOP))
                .add("WalkUp", sheet.createAnimation("WalkUp", 5, 0, 6, 0.08f, PlayMode.LOOP))
                .add("AttackDown", sheet.createAnimation("AttackDown", 6, 0, 4, 0.10f, PlayMode.ONCE))
                .add("AttackRight", sheet.createAnimation("AttackRight", 7, 0, 4, 0.10f, PlayMode.ONCE))
                .add("AttackUp", sheet.createAnimation("AttackUp", 8, 0, 4, 0.10f, PlayMode.ONCE))
                .add("Die", sheet.createAnimation("Die", 9, 0, 3, 0.20f, PlayMode.ONCE));
        anim.play("IdleRight");

        var sprite = new Sprite2DComponent();
        sprite.scale = 8f;

        var transform = new Transform2DComponent();
        transform.position.set(960, 540);

        var player = world.entities.createEntity();
        world.entities.addComponent(player, transform);
        world.entities.addComponent(player, sprite);
        world.entities.addComponent(player, anim);
        world.entities.addComponent(player, new PlayerStateComponent(300f));

        actions = new InputActionMap("Player");
        var moveAction = actions.addAction("Move", ActionType.VECTOR2);
        moveAction.addCompositeBinding().up(Keys.UP).down(Keys.DOWN).left(Keys.LEFT).right(Keys.RIGHT);
        moveAction.addStickCompositeBinding(GamepadAxis.LEFT_X, GamepadAxis.LEFT_Y);

        var attackAction = actions.addAction("Attack").addBinding(Keys.SPACE).addBinding(GamepadButton.A);
        var dieAction = actions.addAction("Die").addBinding(Keys.X).addBinding(GamepadButton.B);
        actions.addAction("Exit").addBinding(Keys.ESCAPE).addBinding(GamepadButton.START).onPerformed(ctx -> game.exit());
        actions.enable();

        world.addSystem(new PlayerControlSystem(moveAction, attackAction, dieAction));

        world.entities.getComponent(cameraEntity, CameraComponent.class).camera.position.set(960, 540);
    }

    @Override
    public void update(GameTime gameTime) {
        world.update(gameTime);
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.WHITE);
        var camera = world.entities.getComponent(cameraEntity, CameraComponent.class).camera;
        spriteBatch.begin(camera.getTransformMatrix());
        world.render(spriteBatch);
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {}

    @Override
    public void dispose() {
        actions.disable();
        content.unload();
        world.dispose();
        spriteBatch.dispose();
    }
}
