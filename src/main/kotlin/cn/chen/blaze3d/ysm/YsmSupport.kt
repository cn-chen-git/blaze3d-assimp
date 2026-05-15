package cn.chen.blaze3d.ysm
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
object YsmSupport : ModelFormatProvider {
    override val support = ModelSupport("ysm", "Yes Steve Model", setOf("ysm", "zip", "json", "geo.json", "bbmodel"), setOf(ModelFeature.TEXT, ModelFeature.SCENE_PACKAGE, ModelFeature.PLAYER_PACKAGE, ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION), setOf("json", "geo.json", "bbmodel", "png", "jpg", "jpeg", "webp"))
    override fun canLoad(path: String) = YsmLoader.canLoad(path)
    override fun load(context: ModelLoadContext) = YsmLoader.load(context.path)
    fun supports(path: String) = canLoad(path)
    fun resources(path: String) = support.resourcesIn(path)
    fun load(path: String) = YsmLoader.load(path)
}
