#version 460
layout(location = 0) in vec3 a_position;
layout(location = 1) in ivec4 a_boneIds;
layout(location = 2) in vec4 a_boneWeights;
layout(std140, binding = 0) uniform Cascade { mat4 lightViewProjection; mat4 modelMatrix; };
layout(std140, binding = 1) uniform Bones { mat4 boneMatrices[256]; };
void main() {
    vec4 pos = vec4(a_position, 1.0);
    vec4 skinned = vec4(0.0);
    float totalWeight = 0.0;
    for (int b = 0; b < 4; ++b) {
        int id = a_boneIds[b]; float w = a_boneWeights[b];
        if (id < 0 || w <= 0.0) continue;
        skinned += boneMatrices[id] * pos * w;
        totalWeight += w;
    }
    if (totalWeight > 0.0) pos = skinned / totalWeight;
    gl_Position = lightViewProjection * modelMatrix * pos;
}
