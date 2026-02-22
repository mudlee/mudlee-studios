#version 450

// Texture sampler — bound via descriptor set (set=0, binding=0, combined image sampler).
// No "TEX_SAMPLER = 0" uniform needed: Vulkan binds textures through descriptor sets.
layout(set = 0, binding = 0) uniform sampler2D texSampler;

layout(location = 0) in vec4 fragColor;
layout(location = 1) in vec2 fragTexCoords;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = texture(texSampler, fragTexCoords);
}

// Compile to SPIR-V:
//   glslc -fshader-stage=fragment frag.glsl -o frag.spv
