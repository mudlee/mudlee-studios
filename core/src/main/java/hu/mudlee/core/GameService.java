package hu.mudlee.core;

/**
 * An optional service that plugs into the {@link Game} loop.
 *
 * <p>Add instances to {@link Game#components}. The game will call {@link #update}, {@link #draw},
 * {@link #resize}, and {@link #dispose} automatically. Override only the methods you need.
 *
 * <pre>
 * game.components.add(new ScreenManager());
 * </pre>
 */
public abstract class GameService {

    public void update(GameTime gameTime) {}

    public void draw(GameTime gameTime) {}

    public void resize(int width, int height) {}

    public void dispose() {}
}
