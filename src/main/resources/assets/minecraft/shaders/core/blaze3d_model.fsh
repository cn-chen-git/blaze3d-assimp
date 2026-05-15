#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
uniform sampler2D NormalMap;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 fragTangent;
in vec3 fragBitangent;
in float toonShade;
out vec4 fragColor;
void main() {
#ifdef MMD_OUTLINE
    fragColor = apply_fog(vec4(0.02, 0.02, 0.025, 1.0) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    return;
#endif
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    vec3 normalTex = texture(NormalMap, texCoord0).xyz * 2.0 - 1.0;
    float normalMix = clamp(length(normalTex.xy), 0.0, 1.0);
    float normalLight = clamp(normalTex.z * 0.5 + dot(normalTex.xy, normalize(vec2(length(fragTangent), length(fragBitangent)))) * 0.08, 0.65, 1.15);
    float cel = mix(0.72, 1.08, floor(toonShade * 3.0) / 2.0);
    color *= vertexColor * ColorModulator;
    color.rgb *= cel * mix(1.0, normalLight, normalMix);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
