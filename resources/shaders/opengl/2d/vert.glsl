#version 410 core
#extension GL_ARB_separate_shader_objects: enable

layout (location=0) in vec3 aPosition;
layout (location=1) in vec4 aColor;
layout (location=2) in vec2 aTexCoords;

uniform mat4 uProjection;
uniform mat4 uView;

out gl_PerVertex {
	vec4 gl_Position;
};

layout (location = 0) out vec4 fColor;
layout (location = 1) out vec2 fTexCoords;

void main()
{
	gl_Position = uProjection * uView * vec4(aPosition, 1.0);
	fColor = aColor;
	fTexCoords = aTexCoords;
}