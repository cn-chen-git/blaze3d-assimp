package cn.chen.blaze3d.render
import cn.chen.blaze3d.math.Vec3
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
data class CompressionBounds(var minX: Float = Float.POSITIVE_INFINITY, var minY: Float = Float.POSITIVE_INFINITY, var minZ: Float = Float.POSITIVE_INFINITY, var maxX: Float = Float.NEGATIVE_INFINITY, var maxY: Float = Float.NEGATIVE_INFINITY, var maxZ: Float = Float.NEGATIVE_INFINITY) {
    fun expand(x: Float, y: Float, z: Float) { if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z; if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z }
    fun expand(v: Vec3) = expand(v.x, v.y, v.z)
    fun merge(o: CompressionBounds) { expand(o.minX, o.minY, o.minZ); expand(o.maxX, o.maxY, o.maxZ) }
    fun reset() { minX = Float.POSITIVE_INFINITY; minY = Float.POSITIVE_INFINITY; minZ = Float.POSITIVE_INFINITY; maxX = Float.NEGATIVE_INFINITY; maxY = Float.NEGATIVE_INFINITY; maxZ = Float.NEGATIVE_INFINITY }
    val rangeX get() = max(maxX - minX, 1e-5f)
    val rangeY get() = max(maxY - minY, 1e-5f)
    val rangeZ get() = max(maxZ - minZ, 1e-5f)
    val valid get() = maxX >= minX && maxY >= minY && maxZ >= minZ
}
object VertexCompression {
    const val UNORM16_MAX = 65535
    @JvmStatic fun packX(v: Float, bounds: CompressionBounds): Int = min(UNORM16_MAX, max(0, (((v - bounds.minX) / bounds.rangeX) * UNORM16_MAX + 0.5f).toInt()))
    @JvmStatic fun packY(v: Float, bounds: CompressionBounds): Int = min(UNORM16_MAX, max(0, (((v - bounds.minY) / bounds.rangeY) * UNORM16_MAX + 0.5f).toInt()))
    @JvmStatic fun packZ(v: Float, bounds: CompressionBounds): Int = min(UNORM16_MAX, max(0, (((v - bounds.minZ) / bounds.rangeZ) * UNORM16_MAX + 0.5f).toInt()))
    @JvmStatic fun unpackX(raw: Int, bounds: CompressionBounds): Float = (raw.toFloat() / UNORM16_MAX) * bounds.rangeX + bounds.minX
    @JvmStatic fun unpackY(raw: Int, bounds: CompressionBounds): Float = (raw.toFloat() / UNORM16_MAX) * bounds.rangeY + bounds.minY
    @JvmStatic fun unpackZ(raw: Int, bounds: CompressionBounds): Float = (raw.toFloat() / UNORM16_MAX) * bounds.rangeZ + bounds.minZ
    @JvmStatic fun writeUnorm16Position(buf: ByteBuffer, x: Float, y: Float, z: Float, bounds: CompressionBounds) {
        buf.putShort(packX(x, bounds).toShort()); buf.putShort(packY(y, bounds).toShort()); buf.putShort(packZ(z, bounds).toShort()); buf.putShort(0)
    }
    @JvmStatic fun decodePosition(buf: ByteBuffer, offset: Int, bounds: CompressionBounds): Vec3 {
        val rawX = buf.getShort(offset).toInt() and 0xFFFF; val rawY = buf.getShort(offset + 2).toInt() and 0xFFFF; val rawZ = buf.getShort(offset + 4).toInt() and 0xFFFF
        return Vec3(unpackX(rawX, bounds), unpackY(rawY, bounds), unpackZ(rawZ, bounds))
    }
    @JvmStatic fun quantizationError(bounds: CompressionBounds): Float = max(bounds.rangeX, max(bounds.rangeY, bounds.rangeZ)) / UNORM16_MAX
    @JvmStatic fun packUV16(u: Float, v: Float): Int {
        val ru = min(UNORM16_MAX, max(0, (u * UNORM16_MAX + 0.5f).toInt())); val rv = min(UNORM16_MAX, max(0, (v * UNORM16_MAX + 0.5f).toInt()))
        return (rv shl 16) or ru
    }
    @JvmStatic fun packCompressedBlob(positions: List<Vec3>, bounds: CompressionBounds, dest: ByteBuffer): Int {
        val start = dest.position(); for (p in positions) writeUnorm16Position(dest, p.x, p.y, p.z, bounds); return dest.position() - start
    }
    fun savingsRatio(originalStride: Int, compressedStride: Int): Float = if (originalStride <= 0) 0f else (originalStride - compressedStride).toFloat() / originalStride
}
class CompressedMeshSegment(val baseVertex: Int, val vertexCount: Int, val bounds: CompressionBounds, val rawData: ByteBuffer, val originalStride: Int, val compressedStride: Int) {
    val sizeBytes get() = rawData.capacity()
    val savings get() = VertexCompression.savingsRatio(originalStride, compressedStride)
}
class CompressedStorage {
    private val segments = ArrayList<CompressedMeshSegment>()
    var totalCompressed = 0L; private set
    var totalOriginal = 0L; private set
    fun add(seg: CompressedMeshSegment) { segments.add(seg); totalCompressed += seg.compressedStride.toLong() * seg.vertexCount; totalOriginal += seg.originalStride.toLong() * seg.vertexCount }
    fun reset() { segments.clear(); totalCompressed = 0; totalOriginal = 0 }
    fun count() = segments.size
    fun status() = "segments=${segments.size} bytes=$totalCompressed/$totalOriginal saved=${if (totalOriginal > 0) (totalOriginal - totalCompressed) * 100 / totalOriginal else 0}%"
}
