#version 450

layout(push_constant) uniform PushConstants {
    mat4 projection;
    mat4 view;
    mat4 model;
} pc;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec4 aColor;

layout(location = 0) out vec4 fragColor;

void main() {
    gl_Position = pc.projection * pc.view * pc.model * vec4(aPosition, 1.0);
    // JOML produces OpenGL-style clip-space Z. Remap for Vulkan's [0, w] range.
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
    fragColor = aColor;
}
