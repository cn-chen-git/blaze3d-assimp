#version 460
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;
layout(std140, binding = 0) uniform BoneMatrices { mat4 bones[256]; };
layout(std430, binding = 1) readonly buffer InputPositions { float inPositions[]; };
layout(std430, binding = 2) readonly buffer InputNormals { float inNormals[]; };
layout(std430, binding = 3) readonly buffer InputTangents { float inTangents[]; };
layout(std430, binding = 4) readonly buffer InputBoneIds { ivec4 inBoneIds[]; };
layout(std430, binding = 5) readonly buffer InputBoneWeights { vec4 inBoneWeights[]; };
layout(std430, binding = 6) writeonly buffer OutPositions { float outPositions[]; };
layout(std430, binding = 7) writeonly buffer OutNormals { float outNormals[]; };
layout(std430, binding = 8) writeonly buffer OutTangents { float outTangents[]; };
layout(std140, binding = 9) uniform Header { uint vertexCount; uint padA; uint padB; uint padC; };
mat3 normalMatrix(mat4 m) {
    vec3 c0 = m[0].xyz; vec3 c1 = m[1].xyz; vec3 c2 = m[2].xyz;
    return mat3(c0, c1, c2);
}
void main() {
    uint id = gl_GlobalInvocationID.x;
    if (id >= vertexCount) return;
    ivec4 ids = inBoneIds[id]; vec4 weights = inBoneWeights[id];
    vec3 pos = vec3(inPositions[id * 3u + 0u], inPositions[id * 3u + 1u], inPositions[id * 3u + 2u]);
    vec3 norm = vec3(inNormals[id * 3u + 0u], inNormals[id * 3u + 1u], inNormals[id * 3u + 2u]);
    vec3 tang = vec3(inTangents[id * 3u + 0u], inTangents[id * 3u + 1u], inTangents[id * 3u + 2u]);
    float totalWeight = 0.0;
    vec3 outPos = vec3(0.0); vec3 outNorm = vec3(0.0); vec3 outTang = vec3(0.0);
    for (int b = 0; b < 4; ++b) {
        int bid = ids[b]; float w = weights[b];
        if (bid < 0 || w <= 0.0) continue;
        mat4 m = bones[bid];
        outPos += (m * vec4(pos, 1.0)).xyz * w;
        mat3 nm = normalMatrix(m);
        outNorm += (nm * norm) * w; outTang += (nm * tang) * w;
        totalWeight += w;
    }
    if (totalWeight > 0.0) { outPos /= totalWeight; outNorm /= totalWeight; outTang /= totalWeight; } else { outPos = pos; outNorm = norm; outTang = tang; }
    outPositions[id * 3u + 0u] = outPos.x; outPositions[id * 3u + 1u] = outPos.y; outPositions[id * 3u + 2u] = outPos.z;
    outNormals[id * 3u + 0u] = outNorm.x; outNormals[id * 3u + 1u] = outNorm.y; outNormals[id * 3u + 2u] = outNorm.z;
    outTangents[id * 3u + 0u] = outTang.x; outTangents[id * 3u + 1u] = outTang.y; outTangents[id * 3u + 2u] = outTang.z;
}
