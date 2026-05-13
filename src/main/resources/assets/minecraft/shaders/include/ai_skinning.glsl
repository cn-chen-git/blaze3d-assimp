#version 330
layout(std140) uniform BoneMatrices {
    mat4 bones[128];
};
mat4 computeSkinMatrix(ivec4 ids, vec4 weights) {
    mat4 m = mat4(0.0);
    if (ids.x >= 0) m += bones[ids.x] * weights.x;
    if (ids.y >= 0) m += bones[ids.y] * weights.y;
    if (ids.z >= 0) m += bones[ids.z] * weights.z;
    if (ids.w >= 0) m += bones[ids.w] * weights.w;
    return m;
}
