#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec2 texCoord2;
in vec4 vertexColor;
in vec4 normal;

out vec4 fragColor;

void main() {
    // 无光照着色器：不采样光贴图(Sampler2)，恒定满亮度 → 发光
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard; // 裁掉贴图透明区域（配合 NO_TRANSPARENCY 不透明混合）
    }
    fragColor = color * ColorModulator;
}
