package cn.chen.blaze3d.math
import java.util.ArrayList
class Slab<T : Any>(private val factory: () -> T, initial: Int = 32) {
    private val free = ArrayList<T>(initial)
    private val live = ArrayList<T>(initial)
    private var allocated = 0; private var reused = 0; private var peak = 0
    init { repeat(initial) { free.add(factory()) } }
    fun acquire(): T {
        val v = if (free.isNotEmpty()) { reused++; free.removeAt(free.lastIndex) } else { allocated++; factory() }
        live.add(v); if (live.size > peak) peak = live.size; return v
    }
    fun cursor(): Int = live.size
    fun rewind(mark: Int) { while (live.size > mark) free.add(live.removeAt(live.lastIndex)) }
    fun status() = "alloc=$allocated reuse=$reused peak=$peak live=${live.size} free=${free.size}"
}
class PoolScope {
    val vec3 = Slab(::Vec3, 64)
    val vec4 = Slab(::Vec4, 32)
    val quat = Slab(::Quat, 32)
    val mat4 = Slab(::Mat4, 32)
    fun v3() = vec3.acquire().set(0f, 0f, 0f)
    fun v3(x: Float, y: Float, z: Float) = vec3.acquire().set(x, y, z)
    fun v3(o: Vec3) = vec3.acquire().set(o.x, o.y, o.z)
    fun v4() = vec4.acquire().set(0f, 0f, 0f, 1f)
    fun v4(x: Float, y: Float, z: Float, w: Float) = vec4.acquire().set(x, y, z, w)
    fun q() = quat.acquire().set(0f, 0f, 0f, 1f)
    fun q(x: Float, y: Float, z: Float, w: Float) = quat.acquire().set(x, y, z, w)
    fun m() = mat4.acquire().setIdentity()
    fun m(o: Mat4) = mat4.acquire().copyFrom(o)
    fun status() = "v3[${vec3.status()}] v4[${vec4.status()}] q[${quat.status()}] m[${mat4.status()}]"
    class Checkpoint(internal val v3: Int, internal val v4: Int, internal val q: Int, internal val m: Int)
    fun checkpoint() = Checkpoint(vec3.cursor(), vec4.cursor(), quat.cursor(), mat4.cursor())
    fun rewind(c: Checkpoint) { vec3.rewind(c.v3); vec4.rewind(c.v4); quat.rewind(c.q); mat4.rewind(c.m) }
}
object Pool {
    private val tls = ThreadLocal.withInitial { PoolScope() }
    fun scope(): PoolScope = tls.get()
    inline fun <R> use(crossinline block: PoolScope.() -> R): R {
        val s = scope(); val c = s.checkpoint(); try { return block(s) } finally { s.rewind(c) }
    }
    fun statusAll() = scope().status()
}
