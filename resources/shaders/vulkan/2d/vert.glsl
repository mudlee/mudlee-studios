#version 450

// Push constants: transformation matrices for the current draw call.
// Uploaded via vkCmdPushConstants (VK_SHADER_STAGE_VERTEX_BIT, 128 bytes).
// This avoids per-frame UBO allocations and matches Vulkan best practice
// for small, frequently changing per-draw data.
layout(push_constant) uniform PushConstants {
    mat4 projection;
    mat4 view;
} pc;

// Vertex attributes — must match the VertexBufferLayout configured in the scene.
// Current layout: position(vec3) + color(vec4) + texCoords(vec2), stride = 36 bytes.
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec4 aColor;
layout(location = 2) in vec2 aTexCoords;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec2 fragTexCoords;

void main() {
    gl_Position = pc.projection * pc.view * vec4(aPosition, 1.0);
    // JOML produces OpenGL-style projection matrices where NDC z is in [-w, w].
    // Vulkan clips to [0, w], so remap: z_vk = (z_gl + w) * 0.5
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
    fragColor    = aColor;
    fragTexCoords = aTexCoords;
}

// Compile to SPIR-V:
//   glslc -fshader-stage=vertex vert.glsl -o vert.spv
