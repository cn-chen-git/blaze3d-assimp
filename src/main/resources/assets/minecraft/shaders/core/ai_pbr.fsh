#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:light.glsl>
uniform sampler2D Sampler0;
uniform sampler2D NormalMap;
uniform sampler2D MetallicRoughnessMap;
uniform sampler2D EmissiveMap;
uniform sampler2D ShadowMap;
uniform sampler2D IrradianceMap;
uniform sampler2D PrefilterMap;
uniform sampler2D BrdfLut;
layout(std140) uniform MaterialFactors {
    vec4 MatBaseColor;
    vec4 MatEmissiveStrength;
    vec4 MatPbr;
    vec4 MatExt0;
    vec4 MatSheenClearcoat;
};
layout(std140) uniform ShadowData {
    mat4 ShadowViewProj[2];
    vec4 ShadowSplits;
    vec4 ShadowParams;
};
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 fragNormal;
in vec3 fragTangent;
in vec3 fragBitangent;
in vec3 fragPos;
in vec3 fragWorldPos;
out vec4 fragColor;
const float PI = 3.14159265359;
const float MIN_ROUGHNESS = 0.045;
const float RGBM_RANGE = 8.0;
const float PREFILTER_LEVELS = 5.0;
float schlick5(float u) { float x = 1.0 - u; float x2 = x * x; return x2 * x2 * x; }
vec3 toLinear(vec3 c) { return c * c; }
vec3 toSrgb(vec3 c) { return sqrt(c); }
vec3 decodeRGBM(vec4 c) { return c.rgb * c.a * RGBM_RANGE; }
vec2 envUv(vec3 n) {
    return vec2(atan(n.z, n.x) / (2.0 * PI) + 0.5, asin(clamp(n.y, -1.0, 1.0)) / PI + 0.5);
}
vec3 sampleIrradiance(vec3 N) { return decodeRGBM(texture(IrradianceMap, envUv(N))); }
vec3 samplePrefilter(vec3 R, float roughness) {
    vec2 uv = envUv(R);
    float maxLv = PREFILTER_LEVELS - 1.0;
    float level = roughness * maxLv;
    float l0 = floor(level);
    float l1 = min(l0 + 1.0, maxLv);
    float f = level - l0;
    float invN = 1.0 / PREFILTER_LEVELS;
    vec2 uv0 = vec2(uv.x, (l0 + uv.y) * invN);
    vec2 uv1 = vec2(uv.x, (l1 + uv.y) * invN);
    return mix(decodeRGBM(texture(PrefilterMap, uv0)), decodeRGBM(texture(PrefilterMap, uv1)), f);
}
float D_GGX(float ndh, float a2) {
    float d = ndh * ndh * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}
float V_SmithGGXCorrelated(float ndv, float ndl, float a2) {
    float ggxv = ndl * sqrt(ndv * ndv * (1.0 - a2) + a2);
    float ggxl = ndv * sqrt(ndl * ndl * (1.0 - a2) + a2);
    return 0.5 / max(ggxv + ggxl, 1e-7);
}
float V_Kelemen(float vdh) { return 0.25 / max(vdh * vdh, 1e-7); }
vec3 F_Schlick(float cosTheta, vec3 f0) { return f0 + (1.0 - f0) * schlick5(cosTheta); }
vec3 F_SchlickR(float cosTheta, vec3 f0, float roughness) {
    return f0 + (max(vec3(1.0 - roughness), f0) - f0) * schlick5(cosTheta);
}
float D_Charlie(float ndh, float sheenRoughness) {
    float invR = 1.0 / max(sheenRoughness, 0.01);
    float sin2h = max(1.0 - ndh * ndh, 0.0);
    return (2.0 + invR) * pow(sin2h, invR * 0.5) / (2.0 * PI);
}
float V_Ashikhmin(float ndv, float ndl) { return 1.0 / (4.0 * (ndl + ndv - ndl * ndv)); }
float sampleShadow(vec3 worldPos, float viewDistance) {
    if (ShadowParams.x < 0.5) return 1.0;
    int c = viewDistance < ShadowSplits.x ? 0 : 1;
    vec4 sp = ShadowViewProj[c] * vec4(worldPos, 1.0);
    vec3 uvz = sp.xyz / max(sp.w, 1e-4) * 0.5 + 0.5;
    if (any(lessThan(uvz, vec3(0.0))) || any(greaterThan(uvz, vec3(1.0)))) return 1.0;
    vec2 atlas = vec2(uvz.x * 0.5 + (c == 0 ? 0.0 : 0.5), uvz.y);
    float texSize = max(ShadowParams.y, 1.0);
    float bias = ShadowParams.z + float(c) * 0.001;
    vec2 texel = 1.0 / vec2(texSize);
    float d0 = texture(ShadowMap, atlas).r;
    float d1 = texture(ShadowMap, atlas + vec2(texel.x, 0.0)).r;
    float d2 = texture(ShadowMap, atlas + vec2(0.0, texel.y)).r;
    float d3 = texture(ShadowMap, atlas + texel).r;
    vec2 f = fract(atlas * texSize);
    float s0 = step(uvz.z - bias, d0);
    float s1 = step(uvz.z - bias, d1);
    float s2 = step(uvz.z - bias, d2);
    float s3 = step(uvz.z - bias, d3);
    return mix(mix(s0, s1, f.x), mix(s2, s3, f.x), f.y);
}
vec3 acesTonemap(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}
struct PbrParams {
    vec3 albedo;
    float metallic;
    float roughness;
    float a2;
    vec3 f0;
    vec3 N;
    vec3 V;
    float ndv;
    float clearcoat;
    float clearcoatRoughness;
    float clearcoatA2;
    vec3 sheenColor;
    float sheenRoughness;
    float transmission;
};
vec3 evalDirectLight(PbrParams p, vec3 L, vec3 lightColor, float intensity) {
    float ndl = max(dot(p.N, L), 0.0);
    if (ndl < 1e-4) return vec3(0.0);
    vec3 H = normalize(p.V + L);
    float ndh = max(dot(p.N, H), 0.0);
    float vdh = max(dot(p.V, H), 0.0);
    float D = D_GGX(ndh, p.a2);
    float V = V_SmithGGXCorrelated(p.ndv, ndl, p.a2);
    vec3 F = F_Schlick(vdh, p.f0);
    vec3 specular = D * V * F;
    vec3 kD = (1.0 - F) * (1.0 - p.metallic);
    vec3 diffuse = kD * p.albedo / PI;
    vec3 baseBrdf = (diffuse + specular) * ndl;
    vec3 sheenOut = vec3(0.0);
    if (p.sheenRoughness > 0.0) {
        sheenOut = p.sheenColor * D_Charlie(ndh, p.sheenRoughness) * V_Ashikhmin(p.ndv, ndl) * ndl;
    }
    float sheenScale = 1.0 - max(max(p.sheenColor.r, p.sheenColor.g), p.sheenColor.b) * 0.2;
    vec3 ccOut = vec3(0.0);
    if (p.clearcoat > 0.0) {
        float Fc = 0.04 + 0.96 * schlick5(vdh);
        ccOut = vec3(D_GGX(ndh, p.clearcoatA2) * V_Kelemen(vdh) * Fc * p.clearcoat * ndl);
    }
    float ccAtten = 1.0 - p.clearcoat * (0.04 + 0.96 * schlick5(p.ndv));
    return (baseBrdf * sheenScale * ccAtten + sheenOut + ccOut) * lightColor * intensity;
}
vec3 evalIBL(PbrParams p, vec3 irradiance, vec3 prefiltered, vec2 brdf) {
    vec3 F = F_SchlickR(p.ndv, p.f0, p.roughness);
    vec3 kD = (1.0 - F) * (1.0 - p.metallic);
    vec3 diffuseIBL = kD * p.albedo * irradiance;
    vec3 FssEss = F * brdf.x + brdf.y;
    float Ems = 1.0 - (brdf.x + brdf.y);
    vec3 Favg = p.f0 + (1.0 - p.f0) / 21.0;
    vec3 FmsEms = Ems * FssEss * Favg / max(1.0 - Favg * Ems, 1e-4);
    vec3 specIBL = prefiltered * (FssEss + FmsEms);
    vec3 sheenIBL = vec3(0.0);
    if (p.sheenRoughness > 0.0) {
        sheenIBL = p.sheenColor * texture(BrdfLut, vec2(p.ndv, p.sheenRoughness)).b * irradiance;
    }
    float sheenScale = 1.0 - max(max(p.sheenColor.r, p.sheenColor.g), p.sheenColor.b) * 0.2;
    vec3 ccIBL = vec3(0.0);
    if (p.clearcoat > 0.0) {
        float Fcc = 0.04 + 0.96 * schlick5(p.ndv);
        vec3 ccR = reflect(-p.V, p.N);
        vec3 ccPrefilt = samplePrefilter(ccR, p.clearcoatRoughness);
        vec2 ccBrdf = texture(BrdfLut, vec2(p.ndv, p.clearcoatRoughness)).rg;
        ccIBL = ccPrefilt * (Fcc * ccBrdf.x + ccBrdf.y) * p.clearcoat;
    }
    float ccAtten = 1.0 - p.clearcoat * (0.04 + 0.96 * schlick5(p.ndv));
    return (diffuseIBL + specIBL) * sheenScale * ccAtten + sheenIBL + ccIBL;
}
void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    float transmission = clamp(MatExt0.w, 0.0, 1.0);
    float alpha = sampled.a * vertexColor.a * ColorModulator.a * MatBaseColor.a;
    alpha *= 1.0 - transmission * 0.5;
#ifdef ALPHA_CUTOUT
    if (alpha < ALPHA_CUTOUT) discard;
#else
    if (alpha < 0.001) discard;
#endif
    vec3 albedo = toLinear(sampled.rgb) * toLinear(vertexColor.rgb) * MatBaseColor.rgb;
    mat3 TBN = mat3(normalize(fragTangent), normalize(fragBitangent), normalize(fragNormal));
    vec3 normalTex = texture(NormalMap, texCoord0).rgb * 2.0 - 1.0;
    normalTex.xy *= MatPbr.z;
    vec2 mr = texture(MetallicRoughnessMap, texCoord0).bg;
    float metallic = clamp(mr.x * MatPbr.x, 0.0, 1.0);
    float roughness = clamp(mr.y * MatPbr.y, MIN_ROUGHNESS, 1.0);
    float a = roughness * roughness;
    float ior = clamp(MatExt0.x, 1.0, 2.5);
    float iorTerm = (ior - 1.0) / (ior + 1.0);
    float dielF0 = iorTerm * iorTerm;
    PbrParams p;
    p.albedo = albedo;
    p.metallic = metallic;
    p.roughness = roughness;
    p.a2 = a * a;
    p.f0 = mix(vec3(dielF0), albedo, metallic);
    p.N = normalize(TBN * normalTex);
    p.V = normalize(-fragPos);
    p.ndv = max(dot(p.N, p.V), 1e-4);
    p.clearcoat = clamp(MatExt0.y, 0.0, 1.0);
    p.clearcoatRoughness = clamp(MatSheenClearcoat.w, MIN_ROUGHNESS, 1.0);
    float ccA = p.clearcoatRoughness * p.clearcoatRoughness;
    p.clearcoatA2 = ccA * ccA;
    float sheenEnabled = MatExt0.z;
    p.sheenColor = sheenEnabled > 0.5 ? MatSheenClearcoat.rgb : vec3(0.0);
    p.sheenRoughness = sheenEnabled > 0.5 ? clamp(p.roughness, MIN_ROUGHNESS, 1.0) : 0.0;
    p.transmission = transmission;
    vec3 mcLight = toLinear(lightMapColor.rgb);
    float lightLuma = dot(mcLight, vec3(0.2126, 0.7152, 0.0722));
    vec3 L0 = normalize(Light0_Direction);
    vec3 L1 = normalize(Light1_Direction);
    vec3 sunColor = vec3(1.0, 0.95, 0.88);
    vec3 fillColor = vec3(0.5, 0.6, 0.85);
    float shadow = sampleShadow(fragWorldPos, length(fragPos));
    vec3 Lo = evalDirectLight(p, L0, sunColor, 3.5 * lightLuma * shadow);
    Lo += evalDirectLight(p, L1, fillColor, 1.2 * lightLuma);
    vec3 irradiance = sampleIrradiance(p.N);
    vec3 R = reflect(-p.V, p.N);
    vec3 prefiltered = samplePrefilter(R, roughness);
    vec2 brdf = texture(BrdfLut, vec2(p.ndv, roughness)).rg;
    vec3 ibl = evalIBL(p, irradiance, prefiltered, brdf);
    vec3 ambient = ibl * mcLight;
    vec3 color = Lo + ambient;
    if (p.transmission > 0.0) color += irradiance * p.albedo * p.transmission * (1.0 - metallic) * mcLight;
    vec2 emPx = 1.5 / vec2(textureSize(EmissiveMap, 0));
    vec3 emCenter = toLinear(texture(EmissiveMap, texCoord0).rgb);
    vec3 emHalo = emCenter * 0.36;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 + vec2(emPx.x, 0.0)).rgb) * 0.12;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 - vec2(emPx.x, 0.0)).rgb) * 0.12;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 + vec2(0.0, emPx.y)).rgb) * 0.12;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 - vec2(0.0, emPx.y)).rgb) * 0.12;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 + emPx).rgb) * 0.04;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 - emPx).rgb) * 0.04;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 + vec2(emPx.x, -emPx.y)).rgb) * 0.04;
    emHalo += toLinear(texture(EmissiveMap, texCoord0 + vec2(-emPx.x, emPx.y)).rgb) * 0.04;
    float emStr = max(MatEmissiveStrength.a, 1.0);
    vec3 emissive = emHalo * MatEmissiveStrength.rgb * emStr;
    float emLuma = dot(emissive, vec3(0.2126, 0.7152, 0.0722));
    if (emLuma > 0.001) {
        color += emissive * 3.5;
        alpha = min(alpha + emLuma * 0.5, 1.0);
    }
    color = mix(toLinear(overlayColor.rgb), color, overlayColor.a);
    color = acesTonemap(color);
    color = toSrgb(color);
    fragColor = apply_fog(vec4(color, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
