package hu.mudlee.core.ui;

import hu.mudlee.core.Color;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.font.BitmapFont;

/**
 * Debug overlay showing FPS, frame time, heap memory, and draw calls.
 *
 * <p>Toggle visibility with <kbd>F3</kbd>.
 *
 * <pre>
 * uiService.getCanvas().create().addComponent(new DebugStatsComponent(uiService.getDefaultFont()));
 * </pre>
 */
public final class DebugStatsComponent extends UIComponent {

    private static final int SAMPLE_COUNT = 60;
    private static final float LINE_HEIGHT = 28f;
    private static final float MARGIN = 10f;
    private static final float COL_GAP = 8f;

    private final float[] frameTimes = new float[SAMPLE_COUNT];
    private final BitmapFont font;
    private final float valueX;
    private int frameIndex = 0;
    private float sumFrameTimes = 0f;

    private boolean visible = true;
    private boolean f3WasDown = false;

    private float averageFps = 0f;
    private float frameTimeMs = 0f;
    private float heapUsedMb = 0f;
    private float heapMaxMb = 0f;
    private int drawCalls = 0;
    private String fpsStr = "";
    private String frameTimeStr = "";
    private String heapStr = "";
    private String drawCallsStr = "";

    public DebugStatsComponent(BitmapFont font) {
        this.font = font;
        var keys = new String[] {"FPS", "Frame time", "Heap", "Draw calls"};
        var maxKeyWidth = 0f;
        for (var key : keys) {
            maxKeyWidth = Math.max(maxKeyWidth, font.measureTextWidth(key));
        }
        valueX = maxKeyWidth + COL_GAP;
    }

    @Override
    public void update(GameTime gameTime) {
        var f3Down = Keyboard.getState().isKeyDown(Keys.F3);
        if (f3Down && !f3WasDown) {
            visible = !visible;
        }
        f3WasDown = f3Down;

        var dt = gameTime.elapsedSeconds();
        sumFrameTimes -= frameTimes[frameIndex];
        frameTimes[frameIndex] = dt;
        sumFrameTimes += dt;
        frameIndex = (frameIndex + 1) % SAMPLE_COUNT;

        averageFps = sumFrameTimes > 0f ? SAMPLE_COUNT / sumFrameTimes : 0f;
        frameTimeMs = dt * 1000f;

        var rt = Runtime.getRuntime();
        heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f);
        heapMaxMb = rt.maxMemory() / (1024f * 1024f);

        drawCalls = Renderer.getDrawCallCount();
        fpsStr = String.format("%.1f", averageFps);
        frameTimeStr = String.format("%.2f ms", frameTimeMs);
        heapStr = String.format("%.0f / %.0f MB", heapUsedMb, heapMaxMb);
        drawCallsStr = String.format("%d", drawCalls);
    }

    @Override
    public void draw(UIBatch batch) {
        if (!visible) {
            return;
        }
        var x = MARGIN;
        var y = MARGIN;
        batch.drawText(font, "FPS", x, y, Color.WHITE);
        batch.drawText(font, fpsStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        batch.drawText(font, "Frame time", x, y, Color.WHITE);
        batch.drawText(font, frameTimeStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        batch.drawText(font, "Heap", x, y, Color.WHITE);
        batch.drawText(font, heapStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        batch.drawText(font, "Draw calls", x, y, Color.WHITE);
        batch.drawText(font, drawCallsStr, x + valueX, y, Color.WHITE);
    }
}
