#version 330
uniform sampler2D Sampler0;
layout(std140) uniform MaterialFactors {
    vec4 MatBaseColor;
    vec4 MatEmissiveStrength;
    vec4 MatPbr;
    vec4 MatExt0;
    vec4 MatSheenClearcoat;
};
in vec2 texCoord0;
in vec4 vertexColor;
void main() {
    float a = texture(Sampler0, texCoord0).a * vertexColor.a * MatBaseColor.a;
    if (a < max(MatPbr.w, 0.01)) discard;
}
