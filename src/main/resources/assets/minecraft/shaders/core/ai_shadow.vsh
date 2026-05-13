#version 330
#moj_import <minecraft:ai_skinning.glsl>
#ifndef SHADOW_CASCADE
#define SHADOW_CASCADE 0
#endif
layout(std140) uniform ObjectMatrices {
    mat4 ObjectToWorld;
    vec4 WorldLight;
};
layout(std140) uniform ShadowData {
    mat4 ShadowViewProj[2];
    vec4 ShadowSplits;
    vec4 ShadowParams;
};
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in vec4 Tangent;
in ivec4 BoneIds;
in vec4 BoneWeights;
out vec2 texCoord0;
out vec4 vertexColor;
void main() {
    vec4 p = vec4(Position, 1.0);
    float w = BoneWeights.x + BoneWeights.y + BoneWeights.z + BoneWeights.w;
    if (w > 0.01) p = computeSkinMatrix(BoneIds, BoneWeights) * p;
    vec4 clip = ShadowViewProj[SHADOW_CASCADE] * ObjectToWorld * p;
    vec2 off = vec2(SHADOW_CASCADE == 0 ? -0.5 : 0.5, 0.0);
    clip.x = clip.x * 0.5 + off.x * clip.w;
    gl_Position = clip;
    texCoord0 = UV0;
    vertexColor = Color;
}
