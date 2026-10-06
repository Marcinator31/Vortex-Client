#version 120

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float BlurFactor;

void main() {
    vec4 cur = texture2D(DiffuseSampler, texCoord);
    vec4 prev = texture2D(PrevSampler, texCoord);
    gl_FragColor = vec4(mix(cur.rgb, prev.rgb, BlurFactor), 1.0);
}
