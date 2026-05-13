#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
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
layout(std140) uniform DynamicLights {
    vec4 LightInfo;
    vec4 CamPosW;
    vec4 PlayerPosW;
    vec4 PlayerColor;
    vec4 LightPos[8];
    vec4 LightCol[8];
};
layout(std140) uniform WorldProbe {
    vec4 ProbeOrigin;
    vec4 ProbeFlags;
    vec4 ProbeSun;
    vec4 ProbeSkyTint;
    vec4 ProbeSunTint;
};
uniform sampler2D ProbeMap;
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
in vec3 fragWorldNormal;
out vec4 fragColor;
const float PI = 3.14159265359;
const float MIN_ROUGHNESS = 0.045;
const float RGBM_RANGE = 8.0;
const float PREFILTER_LEVELS = 5.0;
float schlick5(float u) { float x = 1.0 - u; float x2 = x * x; return x2 * x2 * x; }
vec3 toLinear(vec3 c) { return c * c; }
vec3 toSrgb(vec3 c) { return sqrt(c); }
vec3 decodeRGBM(vec4 c) { return c.rgb * c.a * RGBM_RANGE; }
vec2 envUv(vec3 n) { return vec2(atan(n.z, n.x) / (2.0 * PI) + 0.5, asin(clamp(n.y, -1.0, 1.0)) / PI + 0.5); }
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
float D_GGX(float ndh, float a2) { float d = ndh * ndh * (a2 - 1.0) + 1.0; return a2 / (PI * d * d); }
float V_SmithGGXCorrelated(float ndv, float ndl, float a2) {
    float ggxv = ndl * sqrt(ndv * ndv * (1.0 - a2) + a2);
    float ggxl = ndv * sqrt(ndl * ndl * (1.0 - a2) + a2);
    return 0.5 / max(ggxv + ggxl, 1e-7);
}
float V_Kelemen(float vdh) { return 0.25 / max(vdh * vdh, 1e-7); }
vec3 F_Schlick(float cosTheta, vec3 f0) { return f0 + (1.0 - f0) * schlick5(cosTheta); }
vec3 F_SchlickR(float cosTheta, vec3 f0, float roughness) { return f0 + (max(vec3(1.0 - roughness), f0) - f0) * schlick5(cosTheta); }
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
    float z = uvz.z - bias;
    float s0 = step(z, d0);
    float s1 = step(z, d1);
    float s2 = step(z, d2);
    float s3 = step(z, d3);
    return mix(mix(s0, s1, f.x), mix(s2, s3, f.x), f.y);
}
vec3 acesTonemap(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}
vec4 probeFetchCell(ivec3 c) {
    int gx = max(int(ProbeFlags.w), 1);
    c = clamp(c, ivec3(0), ivec3(gx - 1));
    int sliceX = c.z & 3; int sliceY = c.z >> 2;
    return texelFetch(ProbeMap, ivec2(sliceX * gx + c.x, sliceY * gx + c.y), 0);
}
vec4 probeSample(vec3 worldPos) {
    vec3 local = (worldPos - ProbeOrigin.xyz) / max(ProbeOrigin.w, 1e-4);
    float gxF = ProbeFlags.w;
    if (any(lessThan(local, vec3(0.0))) || any(greaterThanEqual(local, vec3(gxF)))) return vec4(0.5, 0.5, 0.0, 0.0);
    return probeFetchCell(ivec3(floor(local)));
}
float marchBlockShadow(vec3 worldPos, float sunWeight) {
    if (ProbeFlags.x < 0.5 || sunWeight < 0.05) return 1.0;
    vec3 dir = ProbeSun.xyz;
    float step = max(ProbeSun.w, 0.1);
    vec3 p = worldPos + dir * step * 0.6;
    int hits = 0;
    for (int i = 0; i < 10; ++i) {
        p += dir * step;
        if (probeSample(p).b > 0.5) { hits++; if (hits >= 2) return 1.0 - ProbeFlags.y; }
    }
    return mix(1.0, 1.0 - ProbeFlags.y * 0.5, float(hits) / 2.0);
}
float voxelAO(vec3 worldPos, vec3 N) {
    if (ProbeFlags.x < 0.5) return 1.0;
    float w = 0.0; float occ = 0.0;
    for (int i = 0; i < 6; ++i) {
        vec3 o = vec3(0.0);
        if (i == 0) o.x = 1.0; else if (i == 1) o.x = -1.0;
        else if (i == 2) o.y = 1.0; else if (i == 3) o.y = -1.0;
        else if (i == 4) o.z = 1.0; else o.z = -1.0;
        float k = max(dot(o, N), 0.0);
        if (k > 0.0) { occ += probeSample(worldPos + o).b * k; w += k; }
    }
    return 1.0 - clamp(occ / max(w, 1e-3) * 0.55, 0.0, 1.0);
}
float specularAA(vec3 N, float a2) {
    vec3 dnx = dFdx(N); vec3 dny = dFdy(N);
    float variance = dot(dnx, dnx) + dot(dny, dny);
    float kernel = min(variance * 0.25, 0.18);
    return clamp(a2 + kernel, MIN_ROUGHNESS * MIN_ROUGHNESS, 1.0);
}
vec3 burleyDiffuse(vec3 albedo, float ndl, float ndv, float vdh, float roughness) {
    float fd90 = 0.5 + 2.0 * vdh * vdh * roughness;
    float lightScatter = 1.0 + (fd90 - 1.0) * schlick5(ndl);
    float viewScatter = 1.0 + (fd90 - 1.0) * schlick5(ndv);
    return albedo * lightScatter * viewScatter / PI;
}
struct PbrParams {
    vec3 albedo; float metallic; float roughness; float a2; vec3 f0;
    vec3 N; vec3 V; float ndv;
    float clearcoat; float clearcoatRoughness; float clearcoatA2;
    vec3 sheenColor; float sheenRoughness; float transmission;
};
float pointAtten(float distSq, float range) {
    float r2 = max(range * range, 1e-4);
    float k = clamp(1.0 - distSq / r2, 0.0, 1.0);
    return k * k / (1.0 + distSq);
}
vec3 evalPointLight(PbrParams p, vec3 Lw, float distSq, float range, vec3 lightColor, float intensity) {
    float falloff = pointAtten(distSq, range);
    if (falloff < 1e-4) return vec3(0.0);
    vec3 Lview = normalize(mat3(ModelViewMat) * Lw);
    float ndl = max(dot(p.N, Lview), 0.0);
    if (ndl < 1e-4) return vec3(0.0);
    vec3 H = normalize(p.V + Lview);
    float ndh = max(dot(p.N, H), 0.0);
    float vdh = max(dot(p.V, H), 0.0);
    float D = D_GGX(ndh, p.a2);
    float Vt = V_SmithGGXCorrelated(p.ndv, ndl, p.a2);
    vec3 F = F_Schlick(vdh, p.f0);
    vec3 spec = D * Vt * F;
    vec3 kD = (1.0 - F) * (1.0 - p.metallic);
    vec3 diff = kD * p.albedo / PI;
    return (diff + spec) * ndl * lightColor * intensity * falloff;
}
vec3 evalPlayerReflection(PbrParams p, vec3 Nw, vec3 worldPos, vec3 camW, vec3 playerW, vec3 playerColor, float playerR, float playerIntensity) {
    vec3 Vw = normalize(camW - worldPos);
    vec3 Rw = reflect(-Vw, Nw);
    vec3 toP = playerW - worldPos;
    float distP = length(toP);
    if (distP < 1e-3 || distP > 24.0) return vec3(0.0);
    vec3 toPn = toP / distP;
    float angCos = dot(toPn, Rw);
    if (angCos < 0.0) return vec3(0.0);
    float sphereCos = clamp(playerR / max(distP, playerR), 0.0, 0.9999);
    float k = smoothstep(sphereCos, 1.0, angCos);
    float sharp = mix(6.0, 128.0, clamp(1.0 - p.roughness, 0.0, 1.0));
    float glossy = pow(max(angCos, 0.0), sharp);
    float strength = (k * 0.5 + glossy * 0.5) * playerIntensity;
    float distAtten = 1.0 / (1.0 + distP * 0.08);
    vec3 F = F_SchlickR(p.ndv, p.f0, p.roughness);
    return playerColor * F * p.metallic * strength * distAtten * (1.0 - p.roughness * 0.6);
}
vec3 evalDirectLight(PbrParams p, vec3 L, vec3 lightColor, float intensity) {
    float ndl = max(dot(p.N, L), 0.0);
    if (ndl < 1e-4) return vec3(0.0);
    vec3 H = normalize(p.V + L);
    float ndh = max(dot(p.N, H), 0.0);
    float vdh = max(dot(p.V, H), 0.0);
    float D = D_GGX(ndh, p.a2);
    float Vt = V_SmithGGXCorrelated(p.ndv, ndl, p.a2);
    vec3 F = F_Schlick(vdh, p.f0);
    vec3 specular = D * Vt * F;
    vec3 kD = (1.0 - F) * (1.0 - p.metallic);
    vec3 diffuse = kD * burleyDiffuse(p.albedo, ndl, p.ndv, vdh, p.roughness);
    vec3 baseBrdf = (diffuse + specular) * ndl;
    vec3 sheenOut = vec3(0.0);
    if (p.sheenRoughness > 0.0) sheenOut = p.sheenColor * D_Charlie(ndh, p.sheenRoughness) * V_Ashikhmin(p.ndv, ndl) * ndl;
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
    vec3 specIBL = prefiltered * (F * brdf.x + brdf.y);
    vec3 sheenIBL = vec3(0.0);
    if (p.sheenRoughness > 0.0) sheenIBL = p.sheenColor * texture(BrdfLut, vec2(p.ndv, p.sheenRoughness)).b * irradiance;
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
vec3 sampleEmissivePixel(vec2 uv) {
    vec3 e = texture(EmissiveMap, uv).rgb;
    return e * e * (e * 0.6 + 0.4);
}
vec3 emissiveHalo(vec2 uv, float bloomScale) {
    vec2 emPx = (1.0 + bloomScale * 1.4) / vec2(textureSize(EmissiveMap, 0));
    vec3 c = sampleEmissivePixel(uv);
    float wC = 0.227027;
    float w1 = 0.194594;
    float w2 = 0.121622;
    float w3 = 0.054054;
    vec3 sum = c * wC;
    sum += sampleEmissivePixel(uv + vec2(emPx.x, 0.0)) * w1;
    sum += sampleEmissivePixel(uv - vec2(emPx.x, 0.0)) * w1;
    sum += sampleEmissivePixel(uv + vec2(0.0, emPx.y)) * w1;
    sum += sampleEmissivePixel(uv - vec2(0.0, emPx.y)) * w1;
    sum += sampleEmissivePixel(uv + vec2(emPx.x, emPx.y)) * w2;
    sum += sampleEmissivePixel(uv - vec2(emPx.x, emPx.y)) * w2;
    sum += sampleEmissivePixel(uv + vec2(emPx.x, -emPx.y)) * w2;
    sum += sampleEmissivePixel(uv + vec2(-emPx.x, emPx.y)) * w2;
    sum += sampleEmissivePixel(uv + vec2(2.0 * emPx.x, 0.0)) * w3;
    sum += sampleEmissivePixel(uv - vec2(2.0 * emPx.x, 0.0)) * w3;
    sum += sampleEmissivePixel(uv + vec2(0.0, 2.0 * emPx.y)) * w3;
    sum += sampleEmissivePixel(uv - vec2(0.0, 2.0 * emPx.y)) * w3;
    return mix(c, sum, 0.75);
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
    vec3 Nw = normalize(fragWorldNormal);
    float ao = voxelAO(fragWorldPos, Nw);
    p.a2 = specularAA(p.N, p.a2);
    float wetness = ProbeFlags.z * max(Nw.y, 0.0) * clamp(ProbeSkyTint.a, 0.0, 1.0);
    if (wetness > 0.001) {
        roughness = mix(roughness, max(roughness * 0.4, MIN_ROUGHNESS), wetness);
        p.roughness = roughness;
        p.a2 = mix(p.a2, max(roughness * roughness, MIN_ROUGHNESS * MIN_ROUGHNESS), wetness);
        p.f0 = mix(p.f0, vec3(max(p.f0.r, 0.08)), wetness * 0.6);
    }
    vec4 probe = probeSample(fragWorldPos);
    vec2 dynamicLM = vec2(clamp(probe.r * 1.07, 0.0, 1.0), clamp(probe.g * 1.07, 0.0, 1.0));
    vec3 dynMcLight = sample_lightmap(Sampler2, ivec2(dynamicLM * 240.0)).rgb;
    vec3 staticMcLight = lightMapColor.rgb;
    vec3 mcLightSrgb = mix(staticMcLight, dynMcLight, step(0.5, ProbeFlags.x));
    vec3 mcLightLin = toLinear(mcLightSrgb);
    vec3 mcLight = max(mcLightLin, vec3(0.02));
    float lightLuma = dot(mcLight, vec3(0.2126, 0.7152, 0.0722));
    vec3 L0 = normalize(Light0_Direction);
    vec3 L1 = normalize(Light1_Direction);
    vec3 sunColor = mix(vec3(1.0), toLinear(ProbeSunTint.rgb), 0.55);
    float sunInt = ProbeSunTint.a;
    vec3 fillColor = mix(vec3(0.8), toLinear(ProbeSkyTint.rgb), 0.45);
    float skyFac = ProbeSkyTint.a;
    float sunWeight = sunInt * lightLuma;
    float shadow = sunWeight > 0.01 ? sampleShadow(fragWorldPos, length(fragPos)) : 1.0;
    float blockShadow = marchBlockShadow(fragWorldPos, sunWeight);
    float sunMask = shadow * blockShadow;
    vec3 Lo = vec3(0.0);
    if (sunWeight > 0.001) Lo += evalDirectLight(p, L0, sunColor, 1.4 * sunWeight * sunMask);
    Lo += evalDirectLight(p, L1, fillColor, 0.45 * lightLuma * skyFac);
    vec3 irradiance = sampleIrradiance(p.N);
    vec3 R = reflect(-p.V, p.N);
    vec3 prefiltered = samplePrefilter(R, roughness);
    vec2 brdf = texture(BrdfLut, vec2(p.ndv, roughness)).rg;
    vec3 ibl = evalIBL(p, irradiance, prefiltered, brdf);
    vec3 ambient = ibl * mcLight * ao;
    vec3 color = Lo + ambient;
    if (p.transmission > 0.0) color += irradiance * p.albedo * p.transmission * (1.0 - metallic) * mcLight;
    int lightCount = int(LightInfo.x);
    if (lightCount > 0) {
        for (int i = 0; i < 8; ++i) {
            if (i >= lightCount) break;
            vec3 Lw = LightPos[i].xyz - fragWorldPos;
            float d2 = dot(Lw, Lw);
            float range = LightPos[i].w;
            if (d2 > range * range) continue;
            vec3 Lw_n = Lw * inversesqrt(max(d2, 1e-6));
            color += evalPointLight(p, Lw_n, d2, range, LightCol[i].rgb, LightCol[i].a);
        }
    }
    if (LightInfo.y > 0.5 && p.metallic > 0.01) {
        color += evalPlayerReflection(p, Nw, fragWorldPos, CamPosW.xyz, PlayerPosW.xyz, PlayerColor.rgb, PlayerPosW.w, PlayerColor.a);
    }
    float rimIntensity = LightInfo.w;
    if (rimIntensity > 0.001) {
        float rim = pow(1.0 - p.ndv, 3.0);
        vec3 rimColor = mix(vec3(0.55, 0.7, 1.0), vec3(1.0), p.metallic);
        color += rim * rimColor * rimIntensity * mix(0.4, 1.4, p.metallic) * (0.4 + lightLuma * 0.6);
    }
    float emStr = MatEmissiveStrength.a;
    vec3 emFactor = MatEmissiveStrength.rgb;
    float emKey = max(max(emFactor.r, emFactor.g), emFactor.b) * emStr;
    if (emKey > 1e-4) {
        float bloomScale = max(LightInfo.z, 1.0);
        vec3 emCol = emissiveHalo(texCoord0, bloomScale);
        float emMask = dot(emCol, vec3(0.299, 0.587, 0.114));
        emMask = smoothstep(0.008, 0.18, emMask);
        vec3 emissive = emCol * emFactor * emStr * emMask;
        float emLuma = dot(emissive, vec3(0.2126, 0.7152, 0.0722));
        if (emLuma > 0.0005) {
            float bloomBoost = 2.0 + bloomScale * 1.5;
            color += emissive * bloomBoost;
            alpha = min(alpha + emLuma * 0.4 * emMask, 1.0);
        }
    }
    color = mix(toLinear(overlayColor.rgb), color, overlayColor.a);
    color = acesTonemap(color);
    color = toSrgb(color);
    fragColor = apply_fog(vec4(color, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
