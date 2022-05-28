#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

void main() {
	vec3 pos = Position + ChunkOffset;
	gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

	vertexDistance = 1;//TODO cylindrical_distance(ModelViewMat, pos);
	vertexColor = Color;
	texCoord0 = UV0;
	normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
