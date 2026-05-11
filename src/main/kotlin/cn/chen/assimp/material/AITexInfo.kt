package cn.chen.assimp.material
enum class AITexType {
    ALBEDO, NORMAL, METALLIC_ROUGHNESS, OCCLUSION, EMISSIVE,
    SPECULAR, GLOSSINESS, DIFFUSE, HEIGHT, OPACITY,
    CLEARCOAT, CLEARCOAT_ROUGHNESS, CLEARCOAT_NORMAL,
    SHEEN_COLOR, SHEEN_ROUGHNESS, TRANSMISSION,
    THICKNESS, SPECULAR_COLOR, SPECULAR_FACTOR,
    IRIDESCENCE, IRIDESCENCE_THICKNESS, ANISOTROPY
}
data class AITexInfo(
    val path: String,
    val type: AITexType,
    val uvIndex: Int = 0,
    val uvTransform: AIUVTransform = AIUVTransform()
)
data class AIUVTransform(
    val offset: Pair<Float, Float> = 0f to 0f,
    val scale: Pair<Float, Float> = 1f to 1f,
    val rotation: Float = 0f
)
data class AIEmbeddedTex(
    val width: Int,
    val height: Int,
    val data: ByteArray,
    val format: String
)
