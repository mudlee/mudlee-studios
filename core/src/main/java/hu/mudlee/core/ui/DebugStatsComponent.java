package hu.mudlee.core.ui;

import hu.mudlee.core.Color;
import hu.mudlee.core.GameTime;
import hu.mudlee.core.ScreenManager;
import hu.mudlee.core.input.Keyboard;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.font.BitmapFont;
import hu.mudlee.core.window.Window;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Debug overlay showing engine and runtime statistics.
 *
 * <p>Each stat that has a known "bad" threshold is prefixed with a coloured warning indicator:
 * {@code [!]} notice, {@code [!!]} warn, {@code [!!!]} critical. The indicator and value share the
 * same colour; CRITICAL additionally uses a black shadow for maximum contrast.
 *
 * <p>Toggle visibility with <kbd>F3</kbd>.
 *
 * <pre>
 * uiService.getCanvas().create().addComponent(new DebugStatsComponent(uiService.getDefaultFont()));
 * </pre>
 */
public final class DebugStatsComponent extends UIComponent {

    // Thresholds — all higher-is-worse (fps inverted: lower value → worse)
    private static final StatThreshold FPS_THRESHOLD = v -> v < 30
            ? WarningLevel.CRITICAL
            : v < 45 ? WarningLevel.WARN : v < 55 ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold FRAME_TIME_THRESHOLD = v -> v > 33f
            ? WarningLevel.CRITICAL
            : v > 22f ? WarningLevel.WARN : v > 18.67f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold HEAP_PCT_THRESHOLD = v -> v > 85f
            ? WarningLevel.CRITICAL
            : v > 80f ? WarningLevel.WARN : v > 70f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold ALLOC_RATE_THRESHOLD = v -> v > 200f
            ? WarningLevel.CRITICAL
            : v > 100f ? WarningLevel.WARN : v > 50f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold GC_PAUSES_THRESHOLD = v -> v >= 10f
            ? WarningLevel.CRITICAL
            : v >= 3f ? WarningLevel.WARN : v >= 1f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold OFFHEAP_PCT_THRESHOLD = v -> v > 85f
            ? WarningLevel.CRITICAL
            : v > 70f ? WarningLevel.WARN : v > 50f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold DRAW_CALLS_THRESHOLD = v -> v >= 1000f
            ? WarningLevel.CRITICAL
            : v >= 500f ? WarningLevel.WARN : v >= 100f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold FLUSH_COUNT_THRESHOLD = v -> v >= 30f
            ? WarningLevel.CRITICAL
            : v >= 15f ? WarningLevel.WARN : v >= 5f ? WarningLevel.NOTICE : WarningLevel.NONE;
    private static final StatThreshold VERTEX_COUNT_THRESHOLD = v -> v >= 100_000f
            ? WarningLevel.CRITICAL
            : v >= 50_000f ? WarningLevel.WARN : v >= 10_000f ? WarningLevel.NOTICE : WarningLevel.NONE;

    private static final int SAMPLE_COUNT = 60;
    private static final float LINE_HEIGHT = 28f;
    private static final float SECTION_GAP = 6f;
    private static final float MARGIN = 10f;
    private static final float COL_GAP = 8f;

    private final float[] frameTimes = new float[SAMPLE_COUNT];
    private final BitmapFont font;
    private final float valueX;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final List<BufferPoolMXBean> bufferPoolBeans;
    private final long maxDirectMemoryBytes;

    // Static info (read once)
    private String gpuStr = "";
    private String windowStr = "";
    private String backendStr = "";

    // Rolling frame state
    private int frameIndex = 0;
    private float sumFrameTimes = 0f;
    private float minFrameTime = Float.MAX_VALUE;
    private float maxFrameTime = 0f;

    // Toggle
    private boolean visible = true;
    private boolean f3WasDown = false;

    // Per-frame stats (updated in update())
    private float averageFps = 0f;
    private float minFps = 0f;
    private float maxFps = 0f;
    private float frameTimeMs = 0f;
    private float heapPct = 0f;
    private float heapUsedMb = 0f;
    private float heapMaxMb = 0f;
    private float allocRateMbS = 0f;
    private int gcPausesPerSec = 0;
    private float offHeapPct = 0f;
    private float offHeapMb = 0f;
    private int drawCalls = 0;
    private int flushCount = 0;
    private int vertexCount = 0;
    private int textureCount = 0;

    private String uptimeStr = "00:00:00";

    // Heap allocation rate tracking
    private long prevHeapUsedBytes = 0;
    private long allocBytesAccum = 0;
    private float allocAccum = 0f;

    // GC pause tracking
    private long prevGcCount = 0;
    private float gcAccum = 0f;
    private int gcPausesAccum = 0;

    // Display refresh interval (1 second)
    private float displayRefreshAccum = 1f; // start at 1 so first frame populates immediately

    // Cached display strings
    private String fpsStr = "";
    private String fpsMinMaxStr = "";
    private String frameTimeStr = "";
    private String heapStr = "";
    private String allocRateStr = "";
    private String gcStr = "";
    private String offHeapStr = "";
    private String drawCallsStr = "";
    private String flushStr = "";
    private String vertexStr = "";
    private String textureStr = "";

    public DebugStatsComponent(BitmapFont font) {
        this.font = font;
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.bufferPoolBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

        // Approximate max direct memory: JVM exposes it via a private field; fall back to max heap.
        long maxDirect = Runtime.getRuntime().maxMemory();
        try {
            var vmClass = Class.forName("jdk.internal.misc.VM");
            var method = vmClass.getDeclaredMethod("maxDirectMemory");
            method.setAccessible(true);
            maxDirect = (long) method.invoke(null);
        } catch (Exception ignored) {
        }
        this.maxDirectMemoryBytes = maxDirect;

        var keys = new String[] {
            "FPS",
            "Frame time",
            "Heap",
            "Alloc rate",
            "GC pauses",
            "Off-heap",
            "Draw calls",
            "Flushes",
            "Vertices",
            "Textures",
            "Window",
            "GPU",
            "Uptime",
            "Backend"
        };
        var maxKeyWidth = 0f;
        for (var key : keys) {
            maxKeyWidth = Math.max(maxKeyWidth, font.measureTextWidth(key));
        }
        valueX = maxKeyWidth + COL_GAP;
    }

    @Override
    public void start() {
        gpuStr = Renderer.getRendererInfo();
        var size = Window.getSize();
        var ratio = Window.getPixelRatio();
        windowStr = size.x + "x" + size.y + (ratio > 1 ? " @" + ratio + "x" : "");
        backendStr = "Vulkan";
    }

    @Override
    public void update(GameTime gameTime) {
        var f3Down = Keyboard.getState().isKeyDown(Keys.F3);
        if (f3Down && !f3WasDown) {
            visible = !visible;
        }
        f3WasDown = f3Down;

        if (!visible) {
            return;
        }

        var dt = gameTime.elapsedSeconds();

        // Rolling FPS / frame time
        var oldSample = frameTimes[frameIndex];
        sumFrameTimes -= oldSample;
        frameTimes[frameIndex] = dt;
        sumFrameTimes += dt;
        frameIndex = (frameIndex + 1) % SAMPLE_COUNT;
        averageFps = sumFrameTimes > 0f ? SAMPLE_COUNT / sumFrameTimes : 0f;
        frameTimeMs = dt * 1000f;

        // Min/max FPS over the window
        minFrameTime = Math.min(minFrameTime, dt);
        maxFrameTime = Math.max(maxFrameTime, dt);
        // Reset min/max when the rolling window wraps
        if (frameIndex == 0) {
            minFrameTime = Float.MAX_VALUE;
            maxFrameTime = 0f;
            for (var sample : frameTimes) {
                if (sample > 0f) {
                    minFrameTime = Math.min(minFrameTime, sample);
                    maxFrameTime = Math.max(maxFrameTime, sample);
                }
            }
        }
        minFps = maxFrameTime > 0f ? 1f / maxFrameTime : 0f;
        maxFps = minFrameTime > 0f ? 1f / minFrameTime : 0f;

        // Heap allocation rate tracking (must run every frame)
        var rt = Runtime.getRuntime();
        var heapUsedBytes = rt.totalMemory() - rt.freeMemory();
        allocAccum += dt;
        var heapDeltaBytes = Math.max(0L, heapUsedBytes - prevHeapUsedBytes);
        prevHeapUsedBytes = heapUsedBytes;
        allocBytesAccum += heapDeltaBytes;
        if (allocAccum >= 1f) {
            allocRateMbS = allocBytesAccum / (1024f * 1024f);
            allocBytesAccum = 0;
            allocAccum = 0f;
        }

        // GC pause count tracking (must run every frame)
        gcAccum += dt;
        var currentGcCount = 0L;
        for (var bean : gcBeans) {
            currentGcCount += bean.getCollectionCount();
        }
        gcPausesAccum += (int) Math.max(0L, currentGcCount - prevGcCount);
        prevGcCount = currentGcCount;
        if (gcAccum >= 1f) {
            gcPausesPerSec = gcPausesAccum;
            gcPausesAccum = 0;
            gcAccum = 0f;
        }

        // Refresh display values once per second
        displayRefreshAccum += dt;
        if (displayRefreshAccum < 1f) {
            return;
        }
        displayRefreshAccum = 0f;

        // Heap
        heapUsedMb = heapUsedBytes / (1024f * 1024f);
        heapMaxMb = rt.maxMemory() / (1024f * 1024f);
        heapPct = heapMaxMb > 0f ? (heapUsedMb / heapMaxMb) * 100f : 0f;

        // Off-heap direct memory
        var directUsed = 0L;
        for (var pool : bufferPoolBeans) {
            if ("direct".equals(pool.getName())) {
                directUsed = pool.getMemoryUsed();
                break;
            }
        }
        offHeapMb = directUsed / (1024f * 1024f);
        offHeapPct = maxDirectMemoryBytes > 0 ? (directUsed / (float) maxDirectMemoryBytes) * 100f : 0f;

        // Renderer stats
        drawCalls = Renderer.getDrawCallCount();
        vertexCount = Renderer.getVertexCount();
        textureCount = Renderer.getTextureCount();
        flushCount = Renderer.getSpriteBatchFlushCount();

        // Uptime
        var total = (int) gameTime.totalSeconds();
        var h = total / 3600;
        var m = (total % 3600) / 60;
        var s = total % 60;
        uptimeStr = String.format("%02d:%02d:%02d", h, m, s);
        windowStr = buildWindowStr();

        // Build display strings
        fpsStr = String.format("%.1f", averageFps);
        fpsMinMaxStr = String.format("%.1f / %.1f", minFps > 0 ? minFps : averageFps, maxFps > 0 ? maxFps : averageFps);
        frameTimeStr = String.format("%.2f ms", frameTimeMs);
        heapStr = String.format("%.0f%% (%.0f/%.0f MB)", heapPct, heapUsedMb, heapMaxMb);
        allocRateStr = String.format("%.1f MB/s", allocRateMbS);
        gcStr = gcPausesPerSec + " / s";
        offHeapStr = String.format("%.1f MB (%.0f%%)", offHeapMb, offHeapPct);
        drawCallsStr = String.valueOf(drawCalls);
        flushStr = flushCount + " / frame";
        vertexStr = String.format("%,d", vertexCount);
        textureStr = String.valueOf(textureCount);
    }

    @Override
    public void draw(UIBatch batch) {
        if (!visible) {
            return;
        }
        var x = MARGIN;
        var y = MARGIN;

        // Scene — always first, informational only
        var sceneName = ScreenManager.getActiveScreenName();
        if (!sceneName.isEmpty()) {
            drawLabel(batch, "Scene", x, y);
            batch.drawText(font, sceneName, x + valueX, y, Color.WHITE);
            y += LINE_HEIGHT + SECTION_GAP;
        }

        // FPS + min/max on same line
        drawWarned(batch, "FPS", fpsStr, FPS_THRESHOLD.evaluate(averageFps), x, y);
        batch.drawText(font, "Min/Max", x + valueX + font.measureTextWidth(fpsStr) + 20f, y, Color.WHITE);
        batch.drawText(
                font,
                fpsMinMaxStr,
                x + valueX + font.measureTextWidth(fpsStr) + 20f + font.measureTextWidth("Min/Max") + COL_GAP,
                y,
                Color.WHITE);
        y += LINE_HEIGHT;

        drawWarned(batch, "Frame time", frameTimeStr, FRAME_TIME_THRESHOLD.evaluate(frameTimeMs), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Heap", heapStr, HEAP_PCT_THRESHOLD.evaluate(heapPct), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Alloc rate", allocRateStr, ALLOC_RATE_THRESHOLD.evaluate(allocRateMbS), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "GC pauses", gcStr, GC_PAUSES_THRESHOLD.evaluate(gcPausesPerSec), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Off-heap", offHeapStr, OFFHEAP_PCT_THRESHOLD.evaluate(offHeapPct), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Draw calls", drawCallsStr, DRAW_CALLS_THRESHOLD.evaluate(drawCalls), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Flushes", flushStr, FLUSH_COUNT_THRESHOLD.evaluate(flushCount), x, y);
        y += LINE_HEIGHT;
        drawWarned(batch, "Vertices", vertexStr, VERTEX_COUNT_THRESHOLD.evaluate(vertexCount), x, y);
        y += LINE_HEIGHT + SECTION_GAP;

        drawLabel(batch, "Textures", x, y);
        batch.drawText(font, textureStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        drawLabel(batch, "Window", x, y);
        batch.drawText(font, windowStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        drawLabel(batch, "GPU", x, y);
        batch.drawText(font, gpuStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        drawLabel(batch, "Backend", x, y);
        batch.drawText(font, backendStr, x + valueX, y, Color.WHITE);
        y += LINE_HEIGHT;
        drawLabel(batch, "Uptime", x, y);
        batch.drawText(font, uptimeStr, x + valueX, y, Color.WHITE);
    }

    private void drawLabel(UIBatch batch, String label, float x, float y) {
        batch.drawText(font, label, x, y, Color.WHITE);
    }

    private void drawWarned(UIBatch batch, String label, String value, WarningLevel level, float x, float y) {
        batch.drawText(font, label, x, y, Color.WHITE);
        var display = level.prefix + value;
        batch.drawText(font, display, x + valueX, y, level.color, level.shadowColor);
    }

    private String buildWindowStr() {
        var size = Window.getSize();
        var ratio = Window.getPixelRatio();
        return size.x + "x" + size.y + (ratio > 1 ? " @" + ratio + "x" : "");
    }
}
