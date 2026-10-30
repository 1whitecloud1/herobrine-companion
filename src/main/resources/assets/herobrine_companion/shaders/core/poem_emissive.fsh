#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform int OpaqueSurface;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a < 0.001) discard;
    // Weapon alpha marks emission, while ribbon alpha describes actual transparency.
    if (OpaqueSurface != 0) color.a = 1.0;
    color *= vertexColor * ColorModulator;
    if (color.a < 0.001) discard;
    fragColor = color;
}
