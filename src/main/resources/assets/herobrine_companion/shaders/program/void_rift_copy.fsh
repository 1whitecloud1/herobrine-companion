#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Copy the complete rendered color. World-buffer alpha is not screen opacity:
    // sky/horizon pixels can have alpha zero while their RGB is still visible.
    fragColor = texture(DiffuseSampler, texCoord);
}
