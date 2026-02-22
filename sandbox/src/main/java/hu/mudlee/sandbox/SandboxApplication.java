package hu.mudlee.sandbox;

import hu.mudlee.core.*;
import hu.mudlee.core.content.ContentManager;
import hu.mudlee.core.input.InputActionMap;
import hu.mudlee.core.input.Keys;
import hu.mudlee.core.render.RenderBackend;
import hu.mudlee.core.render.Renderer;
import hu.mudlee.core.render.SpriteBatch;
import hu.mudlee.core.render.camera.Camera2D;
import hu.mudlee.core.render.camera.OrthographicCamera;
import hu.mudlee.core.render.texture.Texture2D;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class SandboxApplication extends Game {

    private SpriteBatch spriteBatch;
    private Texture2D marioTexture;
    private Camera2D camera;
    private InputActionMap actions;

    public SandboxApplication() {
        gdm = new GraphicsDeviceManager()
                .setTitle("TESTING")
                .setPreferredBackBufferWidth(1920)
                .setPreferredBackBufferHeight(1080)
                .setVSync(true)
                .setFullscreen(false)
                .setPreferredBackend(RenderBackend.VULKAN);
    }

    @Override
    protected void loadContent() {
        Renderer.setClearColor(new Vector4f(0, 0, 0, 1));

        content = new ContentManager("textures");
        marioTexture = content.load(Texture2D.class, "mario");

        spriteBatch = new SpriteBatch();

        camera = new OrthographicCamera();
        camera.position.x -= 100;
        camera.position.y -= 100;

        actions = new InputActionMap("Game");
        actions.addAction("Exit").addBinding(Keys.ESCAPE).onPerformed(ctx -> exit());
        actions.enable();
    }

    @Override
    protected void update(GameTime gameTime) {
        camera.position.x -= gameTime.elapsedSeconds() * 50f;
        camera.position.y -= gameTime.elapsedSeconds() * 20f;
    }

    @Override
    protected void draw(GameTime gameTime) {
        spriteBatch.begin(camera.getTransformMatrix());
        spriteBatch.draw(marioTexture, new Vector2f(0.5f, 0.5f), Color.WHITE);
        spriteBatch.end();
    }

    @Override
    protected void unloadContent() {
        actions.disable();
        spriteBatch.dispose();
        content.unload();
    }

    public static void main(String[] args) {
        new SandboxApplication().run();
    }
}
