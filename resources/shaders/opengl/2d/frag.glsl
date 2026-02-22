#version 410 core

layout (location=0) in vec4 fColor;
layout (location=1) in vec2 fTexCoords;
layout (location=0) out vec4 FINAL_COLOR;

uniform float uTime;
uniform sampler2D TEX_SAMPLER;

void main()
{
	//float noise = fract(sin(dot(fColor.xy, vec2(12.9898, 78.233))) * 43758.5453);
	//FINAL_COLOR = fColor * noise;
	//FINAL_COLOR = fColor;
	FINAL_COLOR = texture(TEX_SAMPLER, fTexCoords);
}