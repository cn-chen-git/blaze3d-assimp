#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import <minecraft:ai_skinning.glsl>
layout(std140) uniform ObjectMatrices {
    mat4 ObjectToWorld;
    vec4 WorldLight;
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
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 fragNormal;
out vec3 fragTangent;
out vec3 fragBitangent;
out vec3 fragPos;
out vec3 fragWorldPos;
out vec3 fragWorldNormal;
void main() {
    float totalWeight = BoneWeights.x + BoneWeights.y + BoneWeights.z + BoneWeights.w;
    vec4 localPos;
    vec4 worldPos;
    vec3 skinnedNormal;
    vec3 skinnedTangent;
    mat3 skinNorm;
    if (totalWeight > 0.01) {
        mat4 skinMat = computeSkinMatrix(BoneIds, BoneWeights);
        localPos = skinMat * vec4(Position, 1.0);
        worldPos = ModelViewMat * localPos;
        skinNorm = mat3(skinMat);
        skinnedNormal = normalize(mat3(ModelViewMat) * skinNorm * Normal);
        skinnedTangent = normalize(mat3(ModelViewMat) * skinNorm * Tangent.xyz);
    } else {
        localPos = vec4(Position, 1.0);
        worldPos = ModelViewMat * localPos;
        skinNorm = mat3(1.0);
        skinnedNormal = normalize(mat3(ModelViewMat) * Normal);
        skinnedTangent = normalize(mat3(ModelViewMat) * Tangent.xyz);
    }
    gl_Position = ProjMat * worldPos;
    fragPos = worldPos.xyz;
    fragNormal = skinnedNormal;
    fragTangent = skinnedTangent;
    fragBitangent = cross(skinnedNormal, skinnedTangent) * Tangent.w;
    fragWorldPos = (ObjectToWorld * localPos).xyz;
    fragWorldNormal = normalize(mat3(ObjectToWorld) * (skinNorm * Normal));
    sphericalVertexDistance = fog_spherical_distance(worldPos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(worldPos.xyz);
    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, ivec2(int(WorldLight.x) * 16, int(WorldLight.y) * 16));
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
