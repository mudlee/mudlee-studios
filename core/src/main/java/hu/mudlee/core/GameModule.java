package hu.mudlee.core;

/**
 * An optional module that plugs into the {@link Game} loop.
 *
 * <p>Add instances via {@link Game#addModule}. The game will call {@link #update}, {@link #draw},
 * {@link #resize}, and {@link #dispose} automatically. Override only the methods you need.
 *
 * <pre>
 * game.addModule(new ScreenManager());
 * </pre>
 */
public abstract class GameModule {

    public void update(GameTime gameTime) {}

    public void draw(GameTime gameTime) {}

    public void resize(int width, int height) {}

    public void dispose() {}
}
