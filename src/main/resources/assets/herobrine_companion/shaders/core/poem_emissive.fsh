#version 150
uniform sampler2D Sampler0;
uniform int OpaqueSurface;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    if (color.a < 0.001) discard;
    // Low alpha marks emissive pixels in some resource packs, not holes in the blade.
    fragColor = vec4(color.rgb, OpaqueSurface != 0 ? 1.0 : color.a);
}
