package hu.mudlee.sandbox;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.SpriteBatch;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera;
import hu.mudlee.core.render.texture.Texture2D;
import org.joml.Vector2f;

public class GameScreen implements Screen {

    private final Game game;
    private final GraphicsDevice graphicsDevice;

    private ContentManager content;
    private SpriteBatch spriteBatch;
    private Texture2D marioTexture;
    private Camera2D camera;
    private InputActionMap actions;

    public GameScreen(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    @Override
    public void show() {
        content = new ContentManager("textures");
        marioTexture = content.load(Texture2D.class, "mario");

        spriteBatch = new SpriteBatch();

        camera = new OrthographicCamera();
        camera.position.x -= 100;
        camera.position.y -= 100;

        actions = new InputActionMap("Game");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> game.exit());
        actions.enable();
    }

    @Override
    public void update(GameTime gameTime) {
        camera.position.x -= gameTime.elapsedSeconds() * 50f;
        camera.position.y -= gameTime.elapsedSeconds() * 20f;
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        spriteBatch.begin(camera.getTransformMatrix());
        spriteBatch.draw(marioTexture, new Vector2f(0.5f, 0.5f), Color.WHITE);
        spriteBatch.end();
    }

    @Override
    public void dispose() {
        actions.disable();
        spriteBatch.dispose();
        content.unload();
    }
}
