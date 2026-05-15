package cn.chen.blaze3d.material
import cn.chen.blaze3d.math.Vec3
data class KhrClearcoat(
    val factor: Float = 0f,
    val roughnessFactor: Float = 0f,
    val texture: TexInfo? = null,
    val roughnessTexture: TexInfo? = null,
    val normalTexture: TexInfo? = null
)
data class KhrSheen(
    val colorFactor: Vec3 = Vec3(),
    val roughnessFactor: Float = 0f,
    val colorTexture: TexInfo? = null,
    val roughnessTexture: TexInfo? = null
)
data class KhrTransmission(
    val factor: Float = 0f,
    val texture: TexInfo? = null
)
data class KhrVolume(
    val thicknessFactor: Float = 0f,
    val attenuationDistance: Float = Float.MAX_VALUE,
    val attenuationColor: Vec3 = Vec3(1f, 1f, 1f),
    val thicknessTexture: TexInfo? = null
)
data class KhrSpecular(
    val factor: Float = 1f,
    val colorFactor: Vec3 = Vec3(1f, 1f, 1f),
    val texture: TexInfo? = null,
    val colorTexture: TexInfo? = null
)
data class KhrIridescence(
    val factor: Float = 0f,
    val ior: Float = 1.3f,
    val thicknessMinimum: Float = 100f,
    val thicknessMaximum: Float = 400f,
    val texture: TexInfo? = null,
    val thicknessTexture: TexInfo? = null
)
data class KhrAnisotropy(
    val strength: Float = 0f,
    val rotation: Float = 0f,
    val texture: TexInfo? = null
)
data class KhrEmissiveStrength(val strength: Float = 1f)
data class KhrUnlit(val enabled: Boolean = false)
data class KhrExt(
    val clearcoat: KhrClearcoat? = null,
    val sheen: KhrSheen? = null,
    val transmission: KhrTransmission? = null,
    val volume: KhrVolume? = null,
    val specular: KhrSpecular? = null,
    val iridescence: KhrIridescence? = null,
    val anisotropy: KhrAnisotropy? = null,
    val emissiveStrength: KhrEmissiveStrength? = null,
    val unlit: KhrUnlit? = null
)
