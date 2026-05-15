package cn.chen.blaze3d.obj
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.loader.UniversalModelLoader
object ObjSupport : ModelFormatProvider {
    override val support = ModelSupport("obj", "Wavefront OBJ", setOf("obj"), setOf(ModelFeature.TEXT, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES), setOf("mtl", "png", "jpg", "jpeg", "tga", "bmp", "dds"))
    override fun load(context: ModelLoadContext) = UniversalModelLoader.loadFromFile(context.path)
    fun supports(path: String) = support.supports(path)
    fun resources(path: String) = support.resourcesIn(path)
    fun load(path: String) = UniversalModelLoader.loadFromFile(path)
}
