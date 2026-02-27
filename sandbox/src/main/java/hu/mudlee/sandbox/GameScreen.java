package hu.mudlee.sandbox;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.SpriteBatch2D;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera2D;
import hu.mudlee.core.render.texture.Texture2D;
import org.joml.Vector2f;

public class GameScreen implements Screen {

    private final Game game;
    private final GraphicsDevice graphicsDevice;

    private ContentManager content;
    private SpriteBatch2D SpriteBatch2D;
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

        SpriteBatch2D = new SpriteBatch2D();

        camera = new OrthographicCamera2D();
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
        SpriteBatch2D.begin(camera.getTransformMatrix());
        SpriteBatch2D.draw(marioTexture, new Vector2f(0.5f, 0.5f), Color.WHITE);
        SpriteBatch2D.end();
    }

    @Override
    public void dispose() {
        actions.disable();
        SpriteBatch2D.dispose();
        content.unload();
    }
}
