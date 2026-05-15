#version 460
in vec2 v_uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D GAlbedo; uniform sampler2D GNormalRough; uniform sampler2D GMaterial; uniform sampler2D GMotion; uniform sampler2D GDepth;
uniform vec3 SunDirection; uniform vec3 SunColor; uniform vec3 Ambient;
uniform float EmissiveBoost; uniform float ShadowStrength; uniform float SsaoRadius;
uniform mat4 invViewProj; uniform vec3 cameraPos;
vec3 reconstructWorld(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = invViewProj * clip;
    return world.xyz / world.w;
}
float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness; float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float denom = NdotH * NdotH * (a2 - 1.0) + 1.0;
    return a2 / (3.14159265 * denom * denom + 1e-5);
}
float geometrySchlickGGX(float NdotV, float roughness) {
    float k = (roughness + 1.0) * (roughness + 1.0) * 0.125;
    return NdotV / (NdotV * (1.0 - k) + k);
}
float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    return geometrySchlickGGX(max(dot(N, V), 0.0), roughness) * geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
}
vec3 fresnelSchlick(float cosTheta, vec3 F0) { return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0); }
void main() {
    vec4 albedoEmiss = texture(GAlbedo, v_uv);
    vec4 normalRough = texture(GNormalRough, v_uv);
    vec4 material = texture(GMaterial, v_uv);
    float depth = texture(GDepth, v_uv).x;
    if (depth >= 0.9999) { outColor = vec4(0.0); return; }
    vec3 albedo = albedoEmiss.rgb;
    vec3 worldNormal = normalize(normalRough.xyz * 2.0 - 1.0);
    float roughness = normalRough.w;
    float metallic = material.x;
    float emissive = material.y;
    vec3 worldPos = reconstructWorld(v_uv, depth);
    vec3 viewDir = normalize(cameraPos - worldPos);
    vec3 lightDir = normalize(SunDirection);
    vec3 halfDir = normalize(viewDir + lightDir);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    vec3 fresnel = fresnelSchlick(max(dot(halfDir, viewDir), 0.0), F0);
    float NDF = distributionGGX(worldNormal, halfDir, roughness);
    float G = geometrySmith(worldNormal, viewDir, lightDir, roughness);
    vec3 specular = (NDF * G * fresnel) / (4.0 * max(dot(worldNormal, viewDir), 0.0) * max(dot(worldNormal, lightDir), 0.0) + 0.001);
    vec3 kd = (vec3(1.0) - fresnel) * (1.0 - metallic);
    float NdotL = max(dot(worldNormal, lightDir), 0.0);
    vec3 direct = (kd * albedo / 3.14159265 + specular) * SunColor * NdotL;
    vec3 emissiveContribution = albedo * emissive * EmissiveBoost;
    vec3 lit = Ambient * albedo + direct + emissiveContribution;
    outColor = vec4(lit, 1.0);
}
