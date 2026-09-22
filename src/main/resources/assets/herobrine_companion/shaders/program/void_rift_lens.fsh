#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform mat4 InverseProjection;
uniform float RiftCount;
// xy: center UV, z: radius in screen heights, w: screen rotation.
uniform vec4 Rift0, Rift1, Rift2, Rift3, Rift4, Rift5, Rift6, Rift7;
// x: view-space depth, y: opening/closing envelope, z: seed, w: age in seconds.
uniform vec4 RiftState0, RiftState1, RiftState2, RiftState3;
uniform vec4 RiftState4, RiftState5, RiftState6, RiftState7;
in vec2 texCoord;
out vec4 fragColor;

float viewDepth(vec2 uv, float depth) {
    if (depth >= 0.9999999) return 1000000.0;
    vec4 view = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return -view.z / view.w;
}

// Manual bilinear sampling works identically on 1.20/1.21 without altering any world's texture filter.
// All four depth taps must be behind the rift, so foreground silhouettes are never refracted.
vec3 refractScene(vec2 uv, float riftDepth, vec3 unchanged) {
    vec2 pixel = clamp(uv * InSize - 0.5, vec2(0.0), InSize - 1.0);
    ivec2 a = ivec2(floor(pixel));
    ivec2 b = min(a + ivec2(1), ivec2(InSize) - 1);
    float depth = min(min(texelFetch(DepthSampler, a, 0).r,
                          texelFetch(DepthSampler, ivec2(b.x, a.y), 0).r),
                      min(texelFetch(DepthSampler, ivec2(a.x, b.y), 0).r,
                          texelFetch(DepthSampler, b, 0).r));
    if (viewDepth(uv, depth) < riftDepth - 0.015) return unchanged;
    vec2 f = fract(pixel);
    return mix(mix(texelFetch(DiffuseSampler, a, 0).rgb,
                   texelFetch(DiffuseSampler, ivec2(b.x, a.y), 0).rgb, f.x),
               mix(texelFetch(DiffuseSampler, ivec2(a.x, b.y), 0).rgb,
                   texelFetch(DiffuseSampler, b, 0).rgb, f.x), f.y);
}

void lens(inout vec2 displacement, inout float requiredDepth, float sceneDepth, vec4 shape, vec4 state) {
    if (shape.z <= 0.0 || state.y <= 0.0) return;
    vec2 aspect = vec2(InSize.x / InSize.y, 1.0);
    vec2 delta = (texCoord - shape.xy) * aspect / shape.z;
    float radius = length(delta);
    // Exact identity outside the local lens footprint, and in front of its world-space plane.
    if (radius >= 1.6 || sceneDepth < state.x - 0.015) return;
    float visibility = smoothstep(-0.015, 0.10, sceneDepth - state.x);
    float strength = state.y * visibility;
    float c = cos(shape.w), s = sin(shape.w);
    vec2 p = vec2(c * delta.x + s * delta.y, -s * delta.x + c * delta.y);
    float time = state.w;
    float seed = state.z;
    float falloff = 1.0 - smoothstep(0.24, 1.6, radius);
    falloff *= falloff;

    // Gravitational lensing: remap the actual scene UVs with radial pull and a small tangential swirl.
    float pull = min(0.38, 0.22 * falloff / (radius * radius + 0.24)) * strength;
    float twist = 0.18 * strength * falloff * (0.85 + 0.15 * sin(time * 2.4 + seed));
    vec2 warped = mat2(cos(twist), sin(twist), -sin(twist), cos(twist)) * (p * (1.0 - pull));
    vec2 offset = warped - p;
    displacement += vec2(c * offset.x - s * offset.y, s * offset.x + c * offset.y) * shape.z / aspect;
    if (strength * falloff > 0.00001) requiredDepth = max(requiredDepth, state.x);
}

vec3 rift(vec3 color, float sceneDepth, vec4 shape, vec4 state) {
    if (shape.z <= 0.0 || state.y <= 0.0) return color;
    vec2 aspect = vec2(InSize.x / InSize.y, 1.0);
    vec2 delta = (texCoord - shape.xy) * aspect / shape.z;
    float radius = length(delta);
    if (radius >= 1.6 || sceneDepth < state.x - 0.015) return color;
    float strength = state.y * smoothstep(-0.015, 0.10, sceneDepth - state.x);
    float c = cos(shape.w), s = sin(shape.w);
    vec2 p = vec2(c * delta.x + s * delta.y, -s * delta.x + c * delta.y);
    float time = state.w;
    float seed = state.z;
    float falloff = 1.0 - smoothstep(0.24, 1.6, radius);
    falloff *= falloff;

    // An elongated, irregular tear, rather than a textured portal plane.
    float taper = pow(max(0.0, 1.0 - p.y * p.y), 0.85);
    float spine = (0.033 * sin(p.y * 11.0 + seed) + 0.013 * sin(p.y * 31.0 - seed)) * taper;
    float halfWidth = (0.105 + 0.012 * sin(p.y * 8.0 + time * 3.0 + seed)) * taper * (0.55 + 0.45 * state.y);
    float sdf = max(abs(p.x - spine) - halfWidth, (abs(p.y) - 1.0) * 0.65);
    float aa = max(0.003, 1.15 / (InSize.y * shape.z));
    float core = 1.0 - smoothstep(-aa, aa, sdf);
    float endFade = 1.0 - smoothstep(1.0, 1.09, abs(p.y));
    float edge = (1.0 - smoothstep(aa, aa + 0.018, abs(sdf))) * endFade;
    float halo = exp(-max(sdf, 0.0) * 18.0) * endFade * (1.0 - core);
    float pulse = 0.86 + 0.14 * sin(p.y * 18.0 - time * 11.0 + seed);
    color *= 1.0 - 0.16 * falloff * strength;
    color = mix(color, vec3(0.004, 0.001, 0.012), core * strength);
    color += vec3(0.25, 0.075, 0.66) * halo * pulse * strength * 0.68;
    color += vec3(0.95, 0.65, 1.30) * edge * pulse * strength;

    // Faint moving caustics make the space around the cut readable without hiding the world.
    float theta = atan(p.y, p.x + 0.000001);
    float orbit = exp(-abs(radius - 0.64 - 0.035 * sin(theta * 3.0 - time * 2.5 + seed)) * 72.0);
    float arc = pow(max(0.0, sin(theta * 2.0 + time * 2.5 + seed)), 6.0);
    color += vec3(0.16, 0.055, 0.36) * orbit * arc * strength * (1.0 - core);
    return color;
}

void main() {
    ivec2 pixel = clamp(ivec2(gl_FragCoord.xy), ivec2(0), ivec2(InSize) - 1);
    vec4 original = texelFetch(DiffuseSampler, pixel, 0);
    float depth = viewDepth(texCoord, texelFetch(DepthSampler, pixel, 0).r);
    vec3 color = original.rgb;
    vec2 displacement = vec2(0.0);
    float requiredDepth = 0.0;
    if (RiftCount > 0.5) lens(displacement, requiredDepth, depth, Rift0, RiftState0);
    if (RiftCount > 1.5) lens(displacement, requiredDepth, depth, Rift1, RiftState1);
    if (RiftCount > 2.5) lens(displacement, requiredDepth, depth, Rift2, RiftState2);
    if (RiftCount > 3.5) lens(displacement, requiredDepth, depth, Rift3, RiftState3);
    if (RiftCount > 4.5) lens(displacement, requiredDepth, depth, Rift4, RiftState4);
    if (RiftCount > 5.5) lens(displacement, requiredDepth, depth, Rift5, RiftState5);
    if (RiftCount > 6.5) lens(displacement, requiredDepth, depth, Rift6, RiftState6);
    if (RiftCount > 7.5) lens(displacement, requiredDepth, depth, Rift7, RiftState7);
    // Combine the displacement fields and sample once. Mixing shifted/unshifted images would leave double edges.
    float displacementLength = length(displacement * vec2(InSize.x / InSize.y, 1.0));
    if (displacementLength > 0.0) {
        displacement *= min(1.0, 0.085 / displacementLength);
        vec2 uv = clamp(texCoord + displacement, 0.5 / InSize, 1.0 - 0.5 / InSize);
        color = refractScene(uv, requiredDepth, color);
    }
    if (RiftCount > 0.5) color = rift(color, depth, Rift0, RiftState0);
    if (RiftCount > 1.5) color = rift(color, depth, Rift1, RiftState1);
    if (RiftCount > 2.5) color = rift(color, depth, Rift2, RiftState2);
    if (RiftCount > 3.5) color = rift(color, depth, Rift3, RiftState3);
    if (RiftCount > 4.5) color = rift(color, depth, Rift4, RiftState4);
    if (RiftCount > 5.5) color = rift(color, depth, Rift5, RiftState5);
    if (RiftCount > 6.5) color = rift(color, depth, Rift6, RiftState6);
    if (RiftCount > 7.5) color = rift(color, depth, Rift7, RiftState7);
    fragColor = vec4(color, original.a);
}
