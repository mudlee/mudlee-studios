# Deprecated Engine Parts — Status

Date: 2026-03-18

The OpenGL-era HAL leftovers listed in the original version of this file have now been removed.

## Removed

- draw-time topology and polygon mode parameters
- old clear/blend/viewport/scissor no-op APIs
- shader bind/unbind and uniform-location style setup
- texture and render-target native-handle style APIs
- byte-buffer update overloads that were never used
- old OpenGL-shaped enums and dead helper types
- placeholder handle APIs like `Shader.getPipelineId()`, `VertexBuffer.getId()`, and `ElementBuffer.getId()`

## Current status

There is no longer a meaningful deprecated OpenGL-leftover list to implement from this document.
Any future cleanup should be tracked as normal renderer/API improvements rather than as backend-removal
deprecation work.
