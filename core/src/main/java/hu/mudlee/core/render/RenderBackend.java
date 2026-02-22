package hu.mudlee.core.render;

/** Selects the active rendering backend. Configure via {@link Renderer#configure(RenderBackend)}. */
public enum RenderBackend {
  OPENGL,
  VULKAN
}
