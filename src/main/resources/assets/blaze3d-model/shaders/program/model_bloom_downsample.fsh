#version 460
in vec2 v_uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D source;
uniform vec2 sourceTexelSize;
uniform float threshold;
uniform float knee;
uniform int mipIndex;
vec4 brightness(vec4 color) {
    float lum = 0.2126 * color.r + 0.7152 * color.g + 0.0722 * color.b;
    float soft = max(lum - threshold + knee, 0.0); soft = soft * soft / (4.0 * knee + 1e-5);
    float contribution = max(lum - threshold, soft) / max(lum, 1e-5);
    return color * contribution;
}
void main() {
    vec2 uv = v_uv; vec2 offset = sourceTexelSize;
    vec3 a = texture(source, uv + vec2(-2.0, 2.0) * offset).rgb;
    vec3 b = texture(source, uv + vec2(0.0, 2.0) * offset).rgb;
    vec3 c = texture(source, uv + vec2(2.0, 2.0) * offset).rgb;
    vec3 d = texture(source, uv + vec2(-2.0, 0.0) * offset).rgb;
    vec3 e = texture(source, uv).rgb;
    vec3 f = texture(source, uv + vec2(2.0, 0.0) * offset).rgb;
    vec3 g = texture(source, uv + vec2(-2.0, -2.0) * offset).rgb;
    vec3 h = texture(source, uv + vec2(0.0, -2.0) * offset).rgb;
    vec3 i = texture(source, uv + vec2(2.0, -2.0) * offset).rgb;
    vec3 j = texture(source, uv + vec2(-1.0, 1.0) * offset).rgb;
    vec3 k = texture(source, uv + vec2(1.0, 1.0) * offset).rgb;
    vec3 l = texture(source, uv + vec2(-1.0, -1.0) * offset).rgb;
    vec3 m = texture(source, uv + vec2(1.0, -1.0) * offset).rgb;
    vec3 downsample = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    if (mipIndex == 0) outColor = brightness(vec4(downsample, 1.0));
    else outColor = vec4(downsample, 1.0);
}
