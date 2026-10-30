#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 MaskBounds;
in vec2 texCoord;
out vec4 fragColor;

bool inMask(ivec2 pixel) {
    return all(greaterThanEqual(vec2(pixel), MaskBounds.xy))
        && all(lessThan(vec2(pixel), MaskBounds.zw));
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (!inMask(pixel) || texelFetch(Sampler0, pixel, 0).r > 0.5) discard;

    float depth = 1.0;
    bool edge = false;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            ivec2 neighbor = pixel + ivec2(x, y);
            if (inMask(neighbor) && texelFetch(Sampler0, neighbor, 0).r > 0.5) {
                depth = min(depth, texelFetch(Sampler1, neighbor, 0).r);
                edge = true;
            }
        }
    }
    if (!edge) discard;
    // Test the weapon edge against the destination depth without copying or modifying it.
    gl_FragDepth = depth;
    fragColor = vec4(0.35, 0.85, 1.0, 0.85);
}
