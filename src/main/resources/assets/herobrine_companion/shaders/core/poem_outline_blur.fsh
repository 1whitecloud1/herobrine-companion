#version 150

uniform sampler2D Sampler0;
uniform vec2 TexelSize;
in vec2 texCoord;
out vec4 fragColor;

// Sigma 3 pixels, truncated at 6 pixels; weights sum to one.
const float weights[7] = float[7](0.137023, 0.129618, 0.109719, 0.083109, 0.056332, 0.034167, 0.018544);

float maskAt(float x) {
    vec2 uv = texCoord + vec2(x * TexelSize.x, 0.0);
    if (uv.x < 0.0 || uv.x > 1.0) return 0.0;
    return texture(Sampler0, uv).r;
}

void main() {
    float center = maskAt(0.0);
    float edge = max(center, max(maskAt(-1.0), maskAt(1.0)));
    float halo = center * weights[0];
    for (int i = 1; i <= 6; i++) halo += (maskAt(float(i)) + maskAt(-float(i))) * weights[i];
    // Preserve the exact center and dilation separately from the horizontal blur.
    fragColor = vec4(center, edge, halo, 1.0);
}
