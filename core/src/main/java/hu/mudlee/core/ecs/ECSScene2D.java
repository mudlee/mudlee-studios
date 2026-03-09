package hu.mudlee.core.ecs;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.ecs.component.CameraComponent;
import hu.mudlee.core.ecs.system.Animation2DSystem;
import hu.mudlee.core.ecs.system.SpriteRender2DSystem;
import hu.mudlee.core.render.SpriteBatch2D;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera2D;

public abstract class ECSScene2D implements Screen {

    protected final Game game;
    protected final GraphicsDevice graphicsDevice;
    protected final World world = new World();
    protected Color clearColor = Color.WHITE;

    private SpriteBatch2D spriteBatch;
    private Entity cameraEntity;

    protected ECSScene2D(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    protected abstract void onLoad();

    @Override
    public final void show() {
        spriteBatch = new SpriteBatch2D();
        cameraEntity = world.entities.createEntity();
        world.entities.addComponent(cameraEntity, new CameraComponent(new OrthographicCamera2D()));
        world.addSystem(new Animation2DSystem(world.entities));
        world.addSystem(new SpriteRender2DSystem(world.entities));
        onLoad();
    }

    protected Camera2D getCamera() {
        return world.entities.getComponent(cameraEntity, CameraComponent.class).camera;
    }

    @Override
    public void update(GameTime gameTime) {
        world.update(gameTime);
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(clearColor);
        spriteBatch.begin(getCamera().getTransformMatrix());
        world.render(spriteBatch);
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {}

    @Override
    public void dispose() {
        world.dispose();
        spriteBatch.dispose();
    }
}
