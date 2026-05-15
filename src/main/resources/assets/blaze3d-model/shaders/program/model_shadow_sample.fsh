#version 460
in vec3 v_worldPosition; in vec3 v_worldNormal; in vec2 v_uv;
layout(location = 0) out vec4 outShadow;
uniform sampler2DArray shadowCascadeArray;
uniform vec4 cascadeSplits;
uniform mat4 cascadeMatrices[4];
uniform vec4 cascadeBiasArray;
uniform float pcfSamples;
uniform vec3 lightDirection;
float depthBias(int cascade, vec3 normal) {
    return cascadeBiasArray[cascade] * (1.0 - max(dot(normal, lightDirection), 0.0));
}
int chooseCascade(float viewDepth) {
    for (int i = 0; i < 4; ++i) if (viewDepth < cascadeSplits[i]) return i;
    return 3;
}
float sampleCascade(int cascade, vec3 worldPos, vec3 normal, vec4 viewSpace) {
    vec4 shadowCoord = cascadeMatrices[cascade] * vec4(worldPos, 1.0);
    shadowCoord = shadowCoord / shadowCoord.w;
    shadowCoord.xyz = shadowCoord.xyz * 0.5 + 0.5;
    if (shadowCoord.x < 0.0 || shadowCoord.x > 1.0 || shadowCoord.y < 0.0 || shadowCoord.y > 1.0) return 1.0;
    float bias = depthBias(cascade, normal);
    float occlusion = 0.0; float samples = 0.0;
    float radius = 1.0 / 1024.0;
    int steps = int(pcfSamples);
    for (int x = -steps; x <= steps; ++x) for (int y = -steps; y <= steps; ++y) {
        vec2 offset = vec2(x, y) * radius;
        float depth = texture(shadowCascadeArray, vec3(shadowCoord.xy + offset, float(cascade))).r;
        if (shadowCoord.z - bias <= depth) occlusion += 1.0;
        samples += 1.0;
    }
    return occlusion / max(samples, 1.0);
}
void main() {
    vec4 viewSpace = vec4(v_worldPosition, 1.0);
    float viewDepth = -viewSpace.z;
    int cascade = chooseCascade(viewDepth);
    float visibility = sampleCascade(cascade, v_worldPosition, normalize(v_worldNormal), viewSpace);
    outShadow = vec4(vec3(visibility), 1.0);
}
