#version 460
in vec3 v_worldNormal; in vec3 v_worldPosition; in vec2 v_uv; in vec3 v_tangent; in vec3 v_bitangent; in vec4 v_color; in float v_emissive; in float v_metallic; in float v_roughness;
layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormalRough;
layout(location = 2) out vec4 outMaterial;
layout(location = 3) out vec4 outMotion;
uniform sampler2D Sampler0; uniform sampler2D NormalMap;
uniform mat4 previousViewProj; uniform mat4 currentViewProj; uniform vec4 motionBias;
vec3 decodeNormal(vec3 n) { return normalize(n * 2.0 - 1.0); }
void main() {
    vec4 albedo = texture(Sampler0, v_uv) * v_color;
    if (albedo.a < 0.01) discard;
    vec3 tangentNormal = decodeNormal(texture(NormalMap, v_uv).xyz);
    mat3 tbn = mat3(normalize(v_tangent), normalize(v_bitangent), normalize(v_worldNormal));
    vec3 worldNormal = normalize(tbn * tangentNormal);
    vec4 currentClip = currentViewProj * vec4(v_worldPosition, 1.0);
    vec4 previousClip = previousViewProj * vec4(v_worldPosition, 1.0);
    vec2 motion = (currentClip.xy / currentClip.w) - (previousClip.xy / previousClip.w);
    outAlbedo = vec4(albedo.rgb, v_emissive);
    outNormalRough = vec4(worldNormal * 0.5 + 0.5, v_roughness);
    outMaterial = vec4(v_metallic, v_emissive, 1.0, albedo.a);
    outMotion = vec4(motion * motionBias.x, length(motion), 1.0);
}
