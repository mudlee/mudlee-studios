package hu.mudlee.core.render;

import hu.mudlee.core.render.mesh.Mesh3D;

/** Small pass coordinator for explicit 3D mesh draws. */
public final class MeshRenderCoordinator {

    private final RenderTarget renderTarget;
    private final RenderPassOptions passOptions;

    public MeshRenderCoordinator() {
        this(null, RenderPassOptions.clearColor());
    }

    public MeshRenderCoordinator(RenderPassOptions passOptions) {
        this(null, passOptions);
    }

    public MeshRenderCoordinator(RenderTarget renderTarget, RenderPassOptions passOptions) {
        this.renderTarget = renderTarget;
        this.passOptions = passOptions;
    }

    public void begin() {
        Renderer.beginRenderPass(renderTarget, passOptions);
    }

    public void draw(Mesh3D mesh, Shader shader) {
        mesh.draw(shader);
    }

    public void end() {
        Renderer.endRenderPass();
    }
}
