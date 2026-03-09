package hu.mudlee.sandbox;

import hu.mudlee.core.Game;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.ecs.ECSScene2D;
import hu.mudlee.core.ecs.component.Animation2DComponent;
import hu.mudlee.core.ecs.component.Sprite2DComponent;
import hu.mudlee.core.ecs.component.Transform2DComponent;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.animation.PlayMode;
import hu.mudlee.core.render.texture.SpriteSheet2D;
import hu.mudlee.core.render.texture.Texture2D;

public class PlayerScene extends ECSScene2D {

    private ContentManager content;
    private InputActionMap actions;

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        super(game, graphicsDevice);
    }

    @Override
    protected void onLoad() {
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

        world.addSystem(new PlayerControlSystem(world.entities));

        getCamera().position.set(960, 540);

        actions = new InputActionMap("Player");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> game.exit());
        actions.enable();
    }

    @Override
    public void dispose() {
        actions.disable();
        content.unload();
        super.dispose();
    }
}
