#version 410 core

layout (location = 0) out vec4 FINAL_COLOR;
layout (location = 0) in vec4 VERT_OUT_COLOR;

void main()
{
	FINAL_COLOR = VERT_OUT_COLOR;
}