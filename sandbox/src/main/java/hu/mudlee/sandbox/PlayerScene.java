package hu.mudlee.sandbox;

import hu.mudlee.core.Game;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.gameobject.GameObject;
import hu.mudlee.core.gameobject.GameScene2D;
import hu.mudlee.core.gameobject.components.Animator2D;
import hu.mudlee.core.gameobject.components.SpriteRenderer2D;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.animation.PlayMode;
import hu.mudlee.core.render.texture.SpriteSheet2D;
import hu.mudlee.core.render.texture.Texture2D;

public class PlayerScene extends GameScene2D {

    private ContentManager content;
    private InputActionMap actions;

    public PlayerScene(Game game, GraphicsDevice graphicsDevice) {
        super(game, graphicsDevice);
    }

    @Override
    protected void onShow() {
        content = new ContentManager("textures");
        var texture = content.load(Texture2D.class, "sprites/player");
        var sheet = new SpriteSheet2D(texture, 48, 48);

        var animator = new Animator2D();
        animator.addAnimation("IdleDown", sheet.createAnimation("IdleDown", 0, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("IdleRight", sheet.createAnimation("IdleRight", 1, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("IdleUp", sheet.createAnimation("IdleUp", 2, 0, 6, 0.12f, PlayMode.LOOP));
        animator.addAnimation("WalkDown", sheet.createAnimation("WalkDown", 3, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("WalkRight", sheet.createAnimation("WalkRight", 4, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("WalkUp", sheet.createAnimation("WalkUp", 5, 0, 6, 0.08f, PlayMode.LOOP));
        animator.addAnimation("AttackDown", sheet.createAnimation("AttackDown", 6, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("AttackRight", sheet.createAnimation("AttackRight", 7, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("AttackUp", sheet.createAnimation("AttackUp", 8, 0, 4, 0.10f, PlayMode.ONCE));
        animator.addAnimation("Die", sheet.createAnimation("Die", 9, 0, 3, 0.20f, PlayMode.ONCE));
        animator.play("IdleRight");

        var spriteRenderer = new SpriteRenderer2D();
        spriteRenderer.scale = 8f;

        var player = new GameObject("Player");
        player.transform.position.set(960, 540);
        player.addComponent(animator).addComponent(spriteRenderer).addComponent(new PlayerController(300f));

        addGameObject(player);

        camera.position.set(960, 540);

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
