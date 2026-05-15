#version 460
in vec2 v_uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D source;
uniform sampler2D destination;
uniform vec2 sourceTexelSize;
uniform float filterRadius;
uniform float intensity;
void main() {
    vec2 offset = sourceTexelSize * filterRadius;
    vec3 a = texture(source, v_uv + vec2(-1.0, 1.0) * offset).rgb;
    vec3 b = texture(source, v_uv + vec2(0.0, 1.0) * offset).rgb;
    vec3 c = texture(source, v_uv + vec2(1.0, 1.0) * offset).rgb;
    vec3 d = texture(source, v_uv + vec2(-1.0, 0.0) * offset).rgb;
    vec3 e = texture(source, v_uv).rgb;
    vec3 f = texture(source, v_uv + vec2(1.0, 0.0) * offset).rgb;
    vec3 g = texture(source, v_uv + vec2(-1.0, -1.0) * offset).rgb;
    vec3 h = texture(source, v_uv + vec2(0.0, -1.0) * offset).rgb;
    vec3 i = texture(source, v_uv + vec2(1.0, -1.0) * offset).rgb;
    vec3 upsample = e * 0.25 + (b + d + f + h) * 0.125 + (a + c + g + i) * 0.0625;
    vec3 destSample = texture(destination, v_uv).rgb;
    outColor = vec4(destSample + upsample * intensity, 1.0);
}
