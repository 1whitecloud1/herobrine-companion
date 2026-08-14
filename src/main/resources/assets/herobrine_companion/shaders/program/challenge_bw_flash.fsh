#version 150

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;
uniform float FlashProgress;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 halfTexel = vec2(0.5) / max(InSize, vec2(1.0));
    vec2 uv = clamp(texCoord, halfTexel, vec2(1.0) - halfTexel);
    vec4 color = texture(DiffuseSampler, uv);

    float progress = clamp(FlashProgress, 0.0, 1.0);
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float hardLuminance = smoothstep(0.38, 0.62, luminance);
    vec3 monochrome = vec3(hardLuminance);

    float effectMix = 1.0 - smoothstep(0.72, 1.0, progress);
    float whitePhase = 1.0 - smoothstep(0.14, 0.22, progress);
    float blackPhase = smoothstep(0.16, 0.24, progress)
            * (1.0 - smoothstep(0.38, 0.46, progress));

    vec3 result = mix(color.rgb, monochrome, effectMix);
    result = mix(result, vec3(1.0), whitePhase);
    result = mix(result, vec3(0.0), blackPhase * 0.94);
    fragColor = vec4(result, color.a);
}
