package cn.chen.blaze3d.fbx
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.loader.UniversalModelLoader
object FbxSupport : ModelFormatProvider {
    override val support = ModelSupport("fbx", "FBX", setOf("fbx"), setOf(ModelFeature.BINARY, ModelFeature.TEXT, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.MORPHS, ModelFeature.NODES, ModelFeature.CAMERAS, ModelFeature.LIGHTS), setOf("png", "jpg", "jpeg", "tga", "bmp", "dds"))
    override fun load(context: ModelLoadContext) = UniversalModelLoader.loadFromFile(context.path)
    fun supports(path: String) = support.supports(path)
    fun resources(path: String) = support.resourcesIn(path)
}
