package hu.mudlee.core.gameobject;

import hu.mudlee.core.Color;
import hu.mudlee.core.Game;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.GraphicsDevice;
import hu.mudlee.core.Screen;
import hu.mudlee.core.render.SpriteBatch;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Screen} that manages a flat list of {@link GameObject} instances and drives
 * their lifecycle each frame.
 *
 * <p>Integrates directly with {@link hu.mudlee.core.ScreenManager} — push or set a
 * {@code GameScene} just like any other {@code Screen}.
 *
 * <pre>
 * public class PlayerScene extends GameScene {
 *     public PlayerScene(Game game, GraphicsDevice gd) { super(game, gd); }
 *
 *     &#064;Override
 *     protected void onShow() {
 *         var player = new GameObject("Player");
 *         player.addComponent(new SpriteRenderer());
 *         addGameObject(player);
 *     }
 * }
 * </pre>
 */
public abstract class GameScene implements Screen {
    protected final Game game;
    protected final GraphicsDevice graphicsDevice;
    protected Camera2D camera;
    protected SpriteBatch spriteBatch;

    private final List<GameObject> gameObjects = new ArrayList<>();

    protected GameScene(Game game, GraphicsDevice graphicsDevice) {
        this.game = game;
        this.graphicsDevice = graphicsDevice;
    }

    /**
     * Called after the {@link SpriteBatch} and camera have been created. Subclasses set up
     * their {@link GameObject}s here.
     */
    protected abstract void onShow();

    /** Adds a {@link GameObject} to the scene and calls {@link GameObject#start()} on it. */
    protected void addGameObject(GameObject go) {
        gameObjects.add(go);
        go.start();
    }

    /** Removes and disposes a {@link GameObject} from the scene. */
    protected void removeGameObject(GameObject go) {
        if (gameObjects.remove(go)) {
            go.dispose();
        }
    }

    @Override
    public final void show() {
        spriteBatch = new SpriteBatch();
        camera = new OrthographicCamera();
        onShow();
    }

    @Override
    public void update(GameTime gameTime) {
        for (int i = 0; i < gameObjects.size(); i++) {
            gameObjects.get(i).update(gameTime);
        }
    }

    @Override
    public void draw(GameTime gameTime) {
        graphicsDevice.clear(Color.BLACK);
        spriteBatch.begin(camera.getTransformMatrix());
        for (int i = 0; i < gameObjects.size(); i++) {
            gameObjects.get(i).draw(gameTime, spriteBatch);
        }
        spriteBatch.end();
    }

    @Override
    public void dispose() {
        for (int i = gameObjects.size() - 1; i >= 0; i--) {
            gameObjects.get(i).dispose();
        }
        gameObjects.clear();
        spriteBatch.dispose();
    }
}
