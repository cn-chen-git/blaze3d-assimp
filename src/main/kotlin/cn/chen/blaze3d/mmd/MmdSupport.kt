package cn.chen.blaze3d.mmd
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelSupport
object MmdSupport {
    val format = ModelSupport("mmd", "MikuMikuDance", setOf("pmd", "pmx", "vmd", "vpd"), setOf(ModelFeature.BINARY, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.MORPHS, ModelFeature.PHYSICS, ModelFeature.MMD_MOTION), setOf("vmd", "vpd", "png", "jpg", "jpeg", "tga", "bmp"))
    fun supports(path: String) = format.supports(path)
    fun resources(path: String) = format.resourcesIn(path)
}
