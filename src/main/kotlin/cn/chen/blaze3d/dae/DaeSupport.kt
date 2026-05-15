package cn.chen.blaze3d.dae
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.loader.UniversalModelLoader
object DaeSupport : ModelFormatProvider {
    override val support = ModelSupport("dae", "Collada DAE", setOf("dae"), setOf(ModelFeature.TEXT, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.NODES, ModelFeature.CAMERAS, ModelFeature.LIGHTS), setOf("png", "jpg", "jpeg", "tga", "bmp"))
    override fun load(context: ModelLoadContext) = UniversalModelLoader.loadFromFile(context.path)
    fun supports(path: String) = support.supports(path)
    fun resources(path: String) = support.resourcesIn(path)
    fun load(path: String) = UniversalModelLoader.loadFromFile(path)
}
