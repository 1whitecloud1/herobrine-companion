#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;

out vec2 texCoord0;
out vec2 texCoord2;
out vec4 vertexColor;
out vec4 normal;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    texCoord0 = UV0;
    texCoord2 = UV2;
    vertexColor = Color;
    normal = vec4(0.0, 0.0, 1.0, 1.0);
}
