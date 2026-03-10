package hu.mudlee.core.ui;

import hu.mudlee.core.GameService;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.render.font.BitmapFont;
import hu.mudlee.core.window.Window;

/**
 * {@link GameService} that drives the entire UI system each frame.
 *
 * <p>Owns a {@link UIBatch} (backed by {@link hu.mudlee.core.render.SpriteBatch2D}) and a default
 * {@link BitmapFont}. Register once at startup and add to {@code components} after all scene
 * managers so it renders on top:
 *
 * <pre>
 * // in loadContent():
 * var uiService = new UIService();
 * components.add(uiService);
 * uiService.getCanvas().create().addComponent(new DebugStatsComponent());
 * </pre>
 */
public final class UIService extends GameService {

    private static final String DEFAULT_FONT = "/fonts/Inter.ttf";
    private static final float DEFAULT_FONT_SIZE = 22.4f;

    private final UIBatch uiBatch = new UIBatch();
    private final UICanvas canvas = new UICanvas();
    private BitmapFont defaultFont;
    private int screenW, screenH;
    private boolean started = false;

    public UIService() {
        var size = Window.getSize();
        screenW = (int) size.x;
        screenH = (int) size.y;
        uiBatch.resize(screenW, screenH);
        defaultFont = new BitmapFont(DEFAULT_FONT, DEFAULT_FONT_SIZE);
    }

    public UICanvas getCanvas() {
        return canvas;
    }

    public BitmapFont getDefaultFont() {
        return defaultFont;
    }

    public UIBatch getBatch() {
        return uiBatch;
    }

    @Override
    public void update(GameTime gameTime) {
        if (!started) {
            started = true;
            canvas.start();
        }
        canvas.update(gameTime);
    }

    @Override
    public void draw(GameTime gameTime) {
        uiBatch.begin();
        canvas.draw(uiBatch);
        uiBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        screenW = width;
        screenH = height;
        uiBatch.resize(width, height);
    }

    @Override
    public void dispose() {
        canvas.dispose();
        defaultFont.dispose();
        uiBatch.dispose();
    }
}
