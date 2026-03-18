package hu.mudlee.core.render;

import hu.mudlee.core.render.texture.Texture2D;
import hu.mudlee.core.render.vulkan.VulkanRenderBackendFactory;
import hu.mudlee.core.window.WindowEventListener;
import org.joml.Vector4f;

public class Renderer implements WindowEventListener {
    private static int drawCallCount = 0;
    private static int vertexCount = 0;
    private static int textureCount = 0;
    private static int spriteBatchFlushCount = 0;
    private static boolean frameInProgress = false;

    private final GraphicsContext context;
    private final RenderBackendFactory factory;
    private static Renderer instance;
    private static RenderBackend backend = RenderBackend.VULKAN;
    private static RenderBackendFactory backendFactory = createBackendFactory(backend);

    private Renderer() {
        factory = backendFactory();
        context = factory.createGraphicsContext(true);
    }

    /**
     * Selects the rendering backend. Must be called before the first {@link #get()} call. Defaults to
     * {@link RenderBackend#VULKAN} if never called.
     */
    public static void configure(RenderBackend selectedBackend) {
        if (instance != null) {
            throw new IllegalStateException("Renderer already initialised — configure() must be called before get()");
        }
        backend = selectedBackend;
        backendFactory = createBackendFactory(selectedBackend);
    }

    public static RenderBackend activeBackend() {
        return backend;
    }

    public static Renderer get() {
        if (instance == null) {
            instance = new Renderer();
        }

        return instance;
    }

    public static RenderBackendFactory backendFactory() {
        if (backendFactory == null) {
            backendFactory = createBackendFactory(backend);
        }
        return backendFactory;
    }

    @Override
    public void onWindowPrepared() {
        context.windowPrepared();
    }

    @Override
    public void onWindowCreated(long windowId, int width, int height, boolean vSync) {
        context.windowCreated(windowId, width, height, vSync);
    }

    @Override
    public void onWindowResized(int width, int height) {
        context.windowResized(width, height);
    }

    public static void renderRaw(VertexArray vao, Shader shader) {
        if (!frameInProgress) {
            return;
        }
        drawCallCount++;
        get().context.renderRaw(vao, shader);
    }

    public static void renderRaw(VertexArray vao, Shader shader, int elementOffset, int elementCount) {
        if (!frameInProgress) {
            return;
        }
        drawCallCount++;
        get().context.renderRaw(vao, shader, elementOffset, elementCount);
    }

    public static void renderRaw(VertexArray vao, Shader shader, Texture2D texture) {
        if (!frameInProgress) {
            return;
        }
        drawCallCount++;
        get().context.renderRaw(vao, shader, texture);
    }

    public static void renderRaw(
            VertexArray vao, Shader shader, Texture2D texture, int elementOffset, int elementCount) {
        if (!frameInProgress) {
            return;
        }
        drawCallCount++;
        get().context.renderRaw(vao, shader, texture, elementOffset, elementCount);
    }

    public static void beginRenderPass(RenderTarget renderTarget) {
        beginRenderPass(renderTarget, RenderPassOptions.clearColor());
    }

    public static void beginRenderPass(RenderTarget renderTarget, RenderPassOptions options) {
        get().context.beginRenderPass(renderTarget, options);
    }

    public static void endRenderPass() {
        get().context.endRenderPass();
    }

    public static void setClearColor(Vector4f color) {
        get().context.setClearColor(color);
    }

    public static boolean beginFrame() {
        drawCallCount = 0;
        vertexCount = 0;
        spriteBatchFlushCount = 0;
        frameInProgress = get().context.beginFrame();
        return frameInProgress;
    }

    public static void present(float frameTime) {
        if (!frameInProgress) {
            return;
        }
        get().context.present(frameTime);
        frameInProgress = false;
    }

    public static boolean isFrameInProgress() {
        return frameInProgress;
    }

    public static void waitForGPU() {
        get().context.waitIdle();
    }

    public static int getDrawCallCount() {
        return drawCallCount;
    }

    public static void incrementVertexCount(int count) {
        vertexCount += count;
    }

    public static int getVertexCount() {
        return vertexCount;
    }

    public static void incrementTextureCount() {
        textureCount++;
    }

    public static void decrementTextureCount() {
        textureCount--;
    }

    public static int getTextureCount() {
        return textureCount;
    }

    public static void incrementSpriteBatchFlushCount() {
        spriteBatchFlushCount++;
    }

    public static int getSpriteBatchFlushCount() {
        return spriteBatchFlushCount;
    }

    public static String getRendererInfo() {
        return get().context.getRendererInfo();
    }

    public static void dispose() {
        frameInProgress = false;
        get().context.dispose();
    }

    private static RenderBackendFactory createBackendFactory(RenderBackend selectedBackend) {
        return switch (selectedBackend) {
            case VULKAN -> new VulkanRenderBackendFactory();
        };
    }
}
