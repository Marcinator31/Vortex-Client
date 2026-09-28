#version 330

// Vortex Motion Blur: aktuelles Bild mit dem vorigen (schon verwischten) mischen.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, Strength), 1.0);
}
