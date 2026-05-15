package cn.chen.blaze3d.render
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
data class AtlasRegion(val materialIndex: Int, val u0: Float, val v0: Float, val u1: Float, val v1: Float) {
    val scaleU get() = u1 - u0
    val scaleV get() = v1 - v0
    fun remap(u: Float, v: Float): Pair<Float, Float> = (u0 + u * scaleU) to (v0 + v * scaleV)
}
class TextureAtlas(val atlasSize: Int = 2048) {
    private val regions = HashMap<Int, AtlasRegion>()
    private var atlasId: Identifier? = null
    var built = false; private set
    var totalArea = 0; private set
    var usedArea = 0; private set
    val ratio get() = if (totalArea == 0) 0f else usedArea.toFloat() / totalArea
    fun build(sources: List<Pair<Int, NativeImage>>): Identifier? {
        release()
        if (sources.isEmpty()) return null
        val sorted = sources.sortedByDescending { it.second.width * it.second.height }
        val combined = NativeImage(NativeImage.Format.RGBA, atlasSize, atlasSize, false)
        for (y in 0 until atlasSize) for (x in 0 until atlasSize) combined.setPixelABGR(x, y, 0x00000000)
        val shelves = ArrayList<Shelf>()
        for ((matIdx, image) in sorted) {
            val w = min(image.width, atlasSize); val h = min(image.height, atlasSize)
            val placement = place(shelves, w, h) ?: continue
            blit(combined, image, placement.x, placement.y, w, h)
            regions[matIdx] = AtlasRegion(matIdx, placement.x.toFloat() / atlasSize, placement.y.toFloat() / atlasSize, (placement.x + w).toFloat() / atlasSize, (placement.y + h).toFloat() / atlasSize)
            usedArea += w * h
        }
        totalArea = atlasSize * atlasSize
        val id = Identifier.fromNamespaceAndPath("blaze3d-model", "dynamic/model_atlas_${System.nanoTime()}")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "model_atlas" }, combined))
        atlasId = id; built = true
        return id
    }
    private data class Placement(val x: Int, val y: Int)
    private class Shelf(var x: Int, val y: Int, val height: Int)
    private fun place(shelves: ArrayList<Shelf>, w: Int, h: Int): Placement? {
        for (shelf in shelves) if (shelf.x + w <= atlasSize && shelf.height >= h) { val pos = Placement(shelf.x, shelf.y); shelf.x += w; return pos }
        val nextY = shelves.lastOrNull()?.let { it.y + it.height } ?: 0
        if (nextY + h > atlasSize) return null
        val shelf = Shelf(0, nextY, h); shelves.add(shelf); val pos = Placement(0, nextY); shelf.x += w; return pos
    }
    private fun blit(dest: NativeImage, src: NativeImage, dx: Int, dy: Int, w: Int, h: Int) {
        val sw = src.width; val sh = src.height
        for (y in 0 until h) for (x in 0 until w) dest.setPixelABGR(dx + x, dy + y, src.getPixel(min(x, sw - 1), min(y, sh - 1)))
    }
    fun region(materialIndex: Int): AtlasRegion? = regions[materialIndex]
    fun id(): Identifier? = atlasId
    fun release() {
        atlasId?.let { Minecraft.getInstance().textureManager.release(it) }
        atlasId = null; regions.clear(); built = false; totalArea = 0; usedArea = 0
    }
    fun status() = "atlas built=$built size=${atlasSize}x$atlasSize regions=${regions.size} usage=${(ratio * 100).toInt()}%"
    companion object {
        fun suggestSize(textures: List<NativeImage>): Int {
            var sum = 0L; for (t in textures) sum += t.width.toLong() * t.height
            val side = ceil(sqrt(sum.toDouble())).toInt()
            var size = 256; while (size < side && size < 8192) size *= 2
            return max(256, size)
        }
    }
}
class AtlasBatchKey(val atlasId: Identifier?, val normalAtlasId: Identifier?) {
    override fun equals(other: Any?) = other is AtlasBatchKey && atlasId == other.atlasId && normalAtlasId == other.normalAtlasId
    override fun hashCode(): Int = (atlasId?.hashCode() ?: 0) * 31 + (normalAtlasId?.hashCode() ?: 0)
}
