#version 330
layout(std140) uniform BoneMatrices {
    mat4 bones[128];
};
mat4 computeSkinMatrix(ivec4 ids, vec4 weights) {
    mat4 m = bones[ids.x] * weights.x;
    m += bones[ids.y] * weights.y;
    m += bones[ids.z] * weights.z;
    m += bones[ids.w] * weights.w;
    return m;
}
