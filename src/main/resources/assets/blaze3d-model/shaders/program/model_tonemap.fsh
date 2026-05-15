#version 460
in vec2 v_uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D hdrColor; uniform sampler2D bloomColor;
uniform float exposure; uniform float bloomIntensity; uniform float saturation; uniform float invGamma; uniform int tonemapper;
vec3 reinhard(vec3 c) { return c / (1.0 + c); }
vec3 acesFitted(vec3 c) {
    mat3 input = mat3(0.59719, 0.07600, 0.02840, 0.35458, 0.90834, 0.13383, 0.04823, 0.01566, 0.83777);
    mat3 output = mat3(1.60475, -0.10208, -0.00327, -0.53108, 1.10813, -0.07276, -0.07367, -0.00605, 1.07602);
    vec3 v = input * c;
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return clamp(output * (a / b), 0.0, 1.0);
}
vec3 agx(vec3 c) {
    vec3 x = clamp(c, 0.0, 1.0);
    return clamp((x * (x * (x * 17.5128 - 4.4863) + 0.8094) + 0.04) / (x * (x * (x * 12.6939 - 2.7028) + 0.4567) + 0.022), 0.0, 1.0);
}
void main() {
    vec3 scene = texture(hdrColor, v_uv).rgb;
    vec3 bloom = texture(bloomColor, v_uv).rgb;
    vec3 combined = (scene + bloom * bloomIntensity) * exposure;
    vec3 mapped;
    if (tonemapper == 0) mapped = reinhard(combined);
    else if (tonemapper == 1) mapped = acesFitted(combined);
    else mapped = agx(combined);
    float lum = 0.2126 * mapped.r + 0.7152 * mapped.g + 0.0722 * mapped.b;
    mapped = mix(vec3(lum), mapped, saturation);
    outColor = vec4(pow(clamp(mapped, 0.0, 1.0), vec3(invGamma)), 1.0);
}
