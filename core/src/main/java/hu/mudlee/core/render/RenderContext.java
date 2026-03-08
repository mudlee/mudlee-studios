package hu.mudlee.core.render;

/**
 * Marker interface for the per-frame render context passed to {@link
 * hu.mudlee.core.gameobject.Component#draw}.
 *
 * <p>In a 2D scene the context is a {@link SpriteBatch2D}. A future 3D scene will pass its own
 * implementation (e.g. a mesh renderer + camera + light list). Components that need a specific
 * rendering API should pattern-match against the concrete type:
 *
 * <pre>
 * {@literal @}Override
 * public void draw(GameTime gameTime, RenderContext context) {
 *     if (context instanceof SpriteBatch2D batch) {
 *         batch.draw(...);
 *     }
 * }
 * </pre>
 */
public interface RenderContext {}
