package cn.chen.blaze3d.render
import com.mojang.blaze3d.platform.NativeImage
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
object ImageDecoder {
    fun decodeBytes(data: ByteArray): NativeImage? {
        val direct = MemoryUtil.memAlloc(data.size)
        direct.put(data).flip()
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1); val h = stack.mallocInt(1); val ch = stack.mallocInt(1)
            val pixels = stbi_load_from_memory(direct, w, h, ch, 4)
            MemoryUtil.memFree(direct)
            if (pixels == null) return null
            val width = w.get(0); val height = h.get(0)
            val img = NativeImage(NativeImage.Format.RGBA, width, height, false)
            for (y in 0 until height) for (x in 0 until width) {
                val off = (y * width + x) * 4
                val r = pixels.get(off).toInt() and 0xFF
                val g = pixels.get(off + 1).toInt() and 0xFF
                val b = pixels.get(off + 2).toInt() and 0xFF
                val a = pixels.get(off + 3).toInt() and 0xFF
                img.setPixelABGR(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
            stbi_image_free(pixels)
            return img
        }
    }
    fun decodeRaw(w: Int, h: Int, data: ByteArray): NativeImage {
        val img = NativeImage(NativeImage.Format.RGBA, w, h, false)
        for (y in 0 until h) for (x in 0 until w) {
            val off = (y * w + x) * 4
            val r = data[off].toInt() and 0xFF
            val g = data[off + 1].toInt() and 0xFF
            val b = data[off + 2].toInt() and 0xFF
            val a = data[off + 3].toInt() and 0xFF
            img.setPixelABGR(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
        }
        return img
    }
}
