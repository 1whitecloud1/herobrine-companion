#version 150

uniform sampler2D Sampler0;
in vec2 texCoord;
in float vertexAlpha;
out vec4 fragColor;

void main() {
    // Even very low nonzero alpha marks solid weapon pixels in the source texture.
    if (texture(Sampler0, texCoord).a * vertexAlpha < 0.001) discard;
    fragColor = vec4(1.0);
}
