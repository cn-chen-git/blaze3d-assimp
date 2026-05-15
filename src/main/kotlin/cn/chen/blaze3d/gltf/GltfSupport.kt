package cn.chen.blaze3d.gltf
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.loader.UniversalModelLoader
object GltfSupport : ModelFormatProvider {
    val json = ModelSupport("gltf", "glTF JSON", setOf("gltf"), setOf(ModelFeature.TEXT, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.MORPHS, ModelFeature.NODES, ModelFeature.CAMERAS, ModelFeature.LIGHTS), setOf("bin", "png", "jpg", "jpeg", "webp", "ktx", "ktx2"))
    val binary = ModelSupport("glb", "glTF Binary", setOf("glb"), setOf(ModelFeature.BINARY, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.EMBEDDED_TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.MORPHS, ModelFeature.NODES, ModelFeature.CAMERAS, ModelFeature.LIGHTS), setOf("png", "jpg", "jpeg", "webp", "ktx", "ktx2"), "gltf")
    override val support = json
    override fun canLoad(path: String) = json.supports(path) || binary.supports(path)
    override fun load(context: ModelLoadContext) = UniversalModelLoader.loadFromFile(context.path)
    fun supports(path: String) = canLoad(path)
    fun resources(path: String) = (if (binary.supports(path)) binary else json).resourcesIn(path)
}
