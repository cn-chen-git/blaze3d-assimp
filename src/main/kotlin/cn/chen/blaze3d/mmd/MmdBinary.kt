package cn.chen.blaze3d.mmd
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
class MmdBinary(private val data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val remaining get() = buf.remaining()
    val position get() = buf.position()
    fun seek(pos: Int) { buf.position(pos.coerceIn(0, data.size)) }
    fun skip(count: Int) { seek(buf.position() + count) }
    fun u8(): Int = buf.get().toInt() and 255
    fun i8(): Int = buf.get().toInt()
    fun u16(): Int = buf.short.toInt() and 65535
    fun i16(): Int = buf.short.toInt()
    fun i32(): Int = buf.int
    fun f32(): Float = buf.float
    fun bytes(count: Int): ByteArray {
        val len = count.coerceAtMost(buf.remaining())
        val out = ByteArray(len)
        buf.get(out)
        return out
    }
    fun fixedString(count: Int, charset: Charset): String {
        val raw = bytes(count)
        val end = raw.indexOf(0).let { if (it < 0) raw.size else it }
        return String(raw, 0, end, charset).trim()
    }
    fun pmxText(encoding: Charset): String {
        val len = i32()
        if (len <= 0 || len > remaining) return ""
        return String(bytes(len), encoding).trimEnd('\u0000')
    }
    fun index(size: Int): Int = when (size) {
        1 -> i8().let { if (it < 0) -1 else it }
        2 -> i16().let { if (it < 0) -1 else it }
        else -> i32()
    }
}
