package cn.chen.blaze3d.material
enum class TexType {
    ALBEDO, NORMAL, METALLIC_ROUGHNESS, OCCLUSION, EMISSIVE,
    SPECULAR, GLOSSINESS, DIFFUSE, HEIGHT, OPACITY,
    CLEARCOAT, CLEARCOAT_ROUGHNESS, CLEARCOAT_NORMAL,
    SHEEN_COLOR, SHEEN_ROUGHNESS, TRANSMISSION,
    THICKNESS, SPECULAR_COLOR, SPECULAR_FACTOR,
    IRIDESCENCE, IRIDESCENCE_THICKNESS, ANISOTROPY
}
data class TexInfo(
    val path: String,
    val type: TexType,
    val uvIndex: Int = 0,
    val uvTransform: UVTransform = UVTransform()
)
data class UVTransform(
    val offset: Pair<Float, Float> = 0f to 0f,
    val scale: Pair<Float, Float> = 1f to 1f,
    val rotation: Float = 0f
)
data class EmbeddedTex(
    val width: Int,
    val height: Int,
    val data: ByteArray,
    val format: String
)
