package hu.mudlee.core;

/**
 * A discrete game screen — a menu, a gameplay level, a pause overlay, etc.
 *
 * <p>Screens are managed by a {@link ScreenManager} component attached to the {@link Game}.
 * Override only the methods you need; all have empty default implementations.
 *
 * <pre>
 * var sm = new ScreenManager();
 * game.addService(sm);
 * sm.set(new MainMenuScreen(game));
 * </pre>
 */
public interface Screen {

    /** Called once when this screen is first added to the stack. Use for one-time setup. */
    default void show() {}

    /**
     * Called when this screen returns to the top of the stack after being hidden by a
     * {@link ScreenManager#push}. Override to re-enable input or resume paused state.
     * The default implementation delegates to {@link #show()} for backwards compatibility,
     * but screens that do heavy one-time work in {@code show()} should override this separately.
     */
    default void resume() {}

    /** Called every frame while this screen is on top. */
    default void update(GameTime gameTime) {}

    /** Called every frame while this screen is on top, after update. */
    default void draw(GameTime gameTime) {}

    /** Called when the window is resized while this screen is in the stack. */
    default void resize(int width, int height) {}

    /** Called when another screen is pushed on top of this one (or when this screen is popped). */
    default void hide() {}

    /** Called when this screen is permanently removed from the stack. */
    default void dispose() {}
}
