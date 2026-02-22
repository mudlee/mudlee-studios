package hu.mudlee.core;

import hu.mudlee.core.render.RenderBackend;

public class GraphicsDeviceManager {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final String DEFAULT_TITLE = "Game";

    private int preferredBackBufferWidth = DEFAULT_WIDTH;
    private int preferredBackBufferHeight = DEFAULT_HEIGHT;
    private String title = DEFAULT_TITLE;
    private boolean vSync = true;
    private boolean fullscreen = false;
    private RenderBackend preferredBackend = RenderBackend.VULKAN;

    public GraphicsDeviceManager setPreferredBackBufferWidth(int width) {
        this.preferredBackBufferWidth = width;
        return this;
    }

    public GraphicsDeviceManager setPreferredBackBufferHeight(int height) {
        this.preferredBackBufferHeight = height;
        return this;
    }

    public GraphicsDeviceManager setTitle(String title) {
        this.title = title;
        return this;
    }

    public GraphicsDeviceManager setVSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    public GraphicsDeviceManager setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        return this;
    }

    public GraphicsDeviceManager setPreferredBackend(RenderBackend backend) {
        this.preferredBackend = backend;
        return this;
    }

    int getPreferredBackBufferWidth() {
        return preferredBackBufferWidth;
    }

    int getPreferredBackBufferHeight() {
        return preferredBackBufferHeight;
    }

    String getTitle() {
        return title;
    }

    boolean isVSync() {
        return vSync;
    }

    boolean isFullscreen() {
        return fullscreen;
    }

    RenderBackend getPreferredBackend() {
        return preferredBackend;
    }
}
