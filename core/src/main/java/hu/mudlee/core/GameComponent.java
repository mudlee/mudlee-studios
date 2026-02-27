package hu.mudlee.core;

// TODO: rename to GameService? It's not really a component in the same sense as a GameObject.Component, but it's also not really a system in the ECS sense. Maybe GameModule?
/**
 * An optional component that plugs into the {@link Game} loop.
 *
 * <p>Add instances to {@link Game#components}. The game will call {@link #update}, {@link #draw},
 * {@link #resize}, and {@link #dispose} automatically. Override only the methods you need.
 *
 * <pre>
 * game.components.add(new ScreenManager());
 * </pre>
 */
public abstract class GameComponent {

    public void update(GameTime gameTime) {}

    public void draw(GameTime gameTime) {}

    public void resize(int width, int height) {}

    public void dispose() {}
}
