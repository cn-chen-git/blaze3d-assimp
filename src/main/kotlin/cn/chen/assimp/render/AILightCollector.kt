package cn.chen.assimp.render
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import kotlin.math.max
class AILightCollector {
    class Light(val x: Float, val y: Float, val z: Float, val r: Float, val g: Float, val b: Float, val intensity: Float, val range: Float)
    private val tmpPos = BlockPos.MutableBlockPos()
    private val lastList = ArrayList<Light>(AILightsBuffer.MAX_LIGHTS)
    private var lastFrame = -RESCAN_INTERVAL
    private var frame = 0
    private val lastCenter = Vector3f(Float.NaN, Float.NaN, Float.NaN)
    fun collect(modelPos: Vector3f, radius: Int): List<Light> {
        frame++
        val dx = modelPos.x - lastCenter.x; val dy = modelPos.y - lastCenter.y; val dz = modelPos.z - lastCenter.z
        val moved = !lastCenter.x.isFinite() || dx * dx + dy * dy + dz * dz > MOVE_REBUILD * MOVE_REBUILD
        if (!moved && frame - lastFrame < RESCAN_INTERVAL) return lastList
        lastCenter.set(modelPos); lastFrame = frame
        lastList.clear()
        val level = Minecraft.getInstance().level ?: return lastList
        val cx = modelPos.x.toInt(); val cy = modelPos.y.toInt(); val cz = modelPos.z.toInt()
        val r = radius.coerceAtMost(MAX_RADIUS)
        val scratch = ArrayList<Light>(64)
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
            val tint = blockTint(state, emit)
            val intensity = (emit * emit) * 0.18f
            scratch.add(Light(fx, fy, fz, tint[0], tint[1], tint[2], intensity, max(maxR, 2f)))
        }
        scratch.sortBy { val ddx = it.x - modelPos.x; val ddy = it.y - modelPos.y; val ddz = it.z - modelPos.z; ddx * ddx + ddy * ddy + ddz * ddz }
        val take = minOf(scratch.size, AILightsBuffer.MAX_LIGHTS)
        for (i in 0 until take) lastList.add(scratch[i])
        return lastList
    }
    fun invalidate() { lastFrame = -RESCAN_INTERVAL; lastCenter.set(Float.NaN, Float.NaN, Float.NaN); lastList.clear() }
    companion object {
        const val MAX_RADIUS = 12
        const val RESCAN_INTERVAL = 20
        const val MOVE_REBUILD = 1.5f
        private fun blockTint(state: BlockState, emit: Int): FloatArray {
            val path = BuiltInRegistries.BLOCK.getKey(state.block).path
            return when {
                path.contains("redstone") -> floatArrayOf(1f, 0.18f, 0.12f)
                path.contains("soul") -> floatArrayOf(0.35f, 0.75f, 1f)
                path.contains("end_rod") || path.contains("end_portal") -> floatArrayOf(0.55f, 1f, 0.85f)
                path.contains("sea_lantern") || path.contains("conduit") || path.contains("prismarine") -> floatArrayOf(0.45f, 0.95f, 1f)
                path.contains("amethyst") -> floatArrayOf(0.85f, 0.55f, 1f)
                path.contains("verdant_froglight") -> floatArrayOf(0.55f, 1f, 0.6f)
                path.contains("pearlescent_froglight") -> floatArrayOf(1f, 0.7f, 1f)
                path.contains("ochre_froglight") -> floatArrayOf(1f, 0.85f, 0.45f)
                emit >= 14 -> floatArrayOf(1f, 0.92f, 0.7f)
                emit >= 10 -> floatArrayOf(1f, 0.85f, 0.55f)
                else -> floatArrayOf(1f, 0.75f, 0.45f)
            }
        }
    }
}
