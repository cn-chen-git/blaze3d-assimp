package cn.chen.assimp.render
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import kotlin.math.max
class AILightCollector {
    class Light { var x = 0f; var y = 0f; var z = 0f; var r = 0f; var g = 0f; var b = 0f; var intensity = 0f; var range = 0f; var d2 = 0f }
    private val tmpPos = BlockPos.MutableBlockPos()
    private val pool = Array(POOL_SIZE) { Light() }
    private val scratch = ArrayList<Light>(POOL_SIZE)
    private val lastList = ArrayList<Light>(AILightsBuffer.MAX_LIGHTS)
    private val tint = FloatArray(3)
    private val cmp = Comparator<Light> { a, b -> a.d2.compareTo(b.d2) }
    private var lastFrame = -RESCAN_INTERVAL
    private var frame = 0
    private val lastCenter = Vector3f(Float.NaN, Float.NaN, Float.NaN)
    fun collect(modelPos: Vector3f, radius: Int): List<Light> {
        frame++
        val dx = modelPos.x - lastCenter.x; val dy = modelPos.y - lastCenter.y; val dz = modelPos.z - lastCenter.z
        val moved = !lastCenter.x.isFinite() || dx * dx + dy * dy + dz * dz > MOVE_REBUILD * MOVE_REBUILD
        if (!moved && frame - lastFrame < RESCAN_INTERVAL) return lastList
        lastCenter.set(modelPos); lastFrame = frame
        scratch.clear(); lastList.clear()
        val level = Minecraft.getInstance().level ?: return lastList
        val cx = modelPos.x.toInt(); val cy = modelPos.y.toInt(); val cz = modelPos.z.toInt()
        val r = radius.coerceAtMost(MAX_RADIUS)
        var poolIdx = 0
        for (oy in -r..r) for (oz in -r..r) for (ox in -r..r) {
            tmpPos.set(cx + ox, cy + oy, cz + oz)
            val state: BlockState = level.getBlockState(tmpPos)
            val emit = state.getLightEmission()
            if (emit <= 0) continue
            val fx = tmpPos.x + 0.5f; val fy = tmpPos.y + 0.5f; val fz = tmpPos.z + 0.5f
            val ddx = fx - modelPos.x; val ddy = fy - modelPos.y; val ddz = fz - modelPos.z
            val dist2 = ddx * ddx + ddy * ddy + ddz * ddz
            val maxR = emit * 1.3f
            if (dist2 > maxR * maxR) continue
            fillTint(state, emit, tint)
            val l = if (poolIdx < pool.size) pool[poolIdx++] else Light()
            l.x = fx; l.y = fy; l.z = fz
            l.r = tint[0]; l.g = tint[1]; l.b = tint[2]
            l.intensity = (emit * emit) * 0.18f
            l.range = max(maxR, 2f)
            l.d2 = dist2
            scratch.add(l)
        }
        scratch.sortWith(cmp)
        val take = minOf(scratch.size, AILightsBuffer.MAX_LIGHTS)
        for (i in 0 until take) lastList.add(scratch[i])
        return lastList
    }
    fun invalidate() { lastFrame = -RESCAN_INTERVAL; lastCenter.set(Float.NaN, Float.NaN, Float.NaN); lastList.clear(); scratch.clear() }
    companion object {
        const val MAX_RADIUS = 12
        const val RESCAN_INTERVAL = 20
        const val MOVE_REBUILD = 1.5f
        const val POOL_SIZE = 128
        private fun fillTint(state: BlockState, emit: Int, out: FloatArray) {
            val path = BuiltInRegistries.BLOCK.getKey(state.block).path
            when {
                path.contains("redstone") -> { out[0]=1f; out[1]=0.18f; out[2]=0.12f }
                path.contains("soul") -> { out[0]=0.35f; out[1]=0.75f; out[2]=1f }
                path.contains("end_rod") || path.contains("end_portal") -> { out[0]=0.55f; out[1]=1f; out[2]=0.85f }
                path.contains("sea_lantern") || path.contains("conduit") || path.contains("prismarine") -> { out[0]=0.45f; out[1]=0.95f; out[2]=1f }
                path.contains("amethyst") -> { out[0]=0.85f; out[1]=0.55f; out[2]=1f }
                path.contains("verdant_froglight") -> { out[0]=0.55f; out[1]=1f; out[2]=0.6f }
                path.contains("pearlescent_froglight") -> { out[0]=1f; out[1]=0.7f; out[2]=1f }
                path.contains("ochre_froglight") -> { out[0]=1f; out[1]=0.85f; out[2]=0.45f }
                emit >= 14 -> { out[0]=1f; out[1]=0.92f; out[2]=0.7f }
                emit >= 10 -> { out[0]=1f; out[1]=0.85f; out[2]=0.55f }
                else -> { out[0]=1f; out[1]=0.75f; out[2]=0.45f }
            }
        }
    }
}
