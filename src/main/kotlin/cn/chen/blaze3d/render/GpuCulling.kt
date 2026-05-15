package cn.chen.blaze3d.render
import cn.chen.blaze3d.advanced.AccelerationData
import cn.chen.blaze3d.advanced.MeshCluster
import cn.chen.blaze3d.math.AABB
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.phys.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
data class IndirectDrawCommand(val indexCount: Int, val instanceCount: Int, val firstIndex: Int, val baseVertex: Int, val baseInstance: Int) {
    fun writeTo(buf: ByteBuffer) { buf.putInt(indexCount); buf.putInt(instanceCount); buf.putInt(firstIndex); buf.putInt(baseVertex); buf.putInt(baseInstance) }
}
data class CulledDraw(val cluster: MeshCluster, val command: IndirectDrawCommand, val passKey: Long)
class FrustumCuller(private val data: AccelerationData) {
    private val nodeStack = IntArray(256)
    private val visibleClusters = ArrayList<MeshCluster>(64)
    private val testCount = AtomicInteger()
    private val rejectCount = AtomicInteger()
    fun cull(frustum: Frustum?, scale: Float, camera: Vec3, lodBias: Float): List<MeshCluster> {
        visibleClusters.clear()
        if (data.nodes.isEmpty()) return visibleClusters
        var sp = 0; nodeStack[sp++] = 0
        while (sp > 0) {
            val nodeIdx = nodeStack[--sp]; val node = data.nodes[nodeIdx]
            testCount.incrementAndGet()
            if (!isAabbVisible(node.bounds, frustum, scale)) { rejectCount.incrementAndGet(); continue }
            if (node.leaf) {
                val cluster = data.clusters.getOrNull(node.clusterIndex) ?: continue
                if (passLod(cluster, camera, scale, lodBias)) visibleClusters.add(cluster)
            } else {
                if (sp + 2 > nodeStack.size) continue
                if (node.right >= 0) nodeStack[sp++] = node.right
                if (node.left >= 0) nodeStack[sp++] = node.left
            }
        }
        return visibleClusters
    }
    private fun passLod(cluster: MeshCluster, camera: Vec3, scale: Float, lodBias: Float): Boolean {
        val cx = ((cluster.bounds.min.x + cluster.bounds.max.x) * 0.5 * scale - camera.x)
        val cy = ((cluster.bounds.min.y + cluster.bounds.max.y) * 0.5 * scale - camera.y)
        val cz = ((cluster.bounds.min.z + cluster.bounds.max.z) * 0.5 * scale - camera.z)
        val d2 = cx * cx + cy * cy + cz * cz
        val target = if (d2 > 19600.0 * lodBias) 2 else if (d2 > 4096.0 * lodBias) 1 else 0
        return cluster.lodLevel >= target
    }
    private fun isAabbVisible(bounds: AABB, frustum: Frustum?, scale: Float): Boolean {
        if (frustum == null) return true
        val mn = bounds.min; val mx = bounds.max
        return frustum.isVisible(net.minecraft.world.phys.AABB(mn.x.toDouble() * scale, mn.y.toDouble() * scale, mn.z.toDouble() * scale, mx.x.toDouble() * scale, mx.y.toDouble() * scale, mx.z.toDouble() * scale))
    }
    fun status() = "frustum tests=${testCount.get()} reject=${rejectCount.get()} visible=${visibleClusters.size}"
}
class IndirectDrawBuilder {
    private val draws = ArrayList<CulledDraw>(128)
    private val byPass = HashMap<Long, ArrayList<CulledDraw>>()
    fun reset() { draws.clear(); byPass.clear() }
    fun add(cluster: MeshCluster, indexCount: Int, firstIndex: Int, baseVertex: Int, baseInstance: Int = 0, instanceCount: Int = 1, passKey: Long = 0L): CulledDraw {
        val cmd = IndirectDrawCommand(indexCount, instanceCount, firstIndex, baseVertex, baseInstance)
        val d = CulledDraw(cluster, cmd, passKey)
        draws.add(d); byPass.getOrPut(passKey) { ArrayList() }.add(d)
        return d
    }
    fun groupedByPass(): Map<Long, List<CulledDraw>> = byPass
    fun all(): List<CulledDraw> = draws
    fun encodeAll(buf: ByteBuffer) { for (d in draws) d.command.writeTo(buf) }
    fun totalCommands() = draws.size
}
class GpuCullingDispatch(private val acceleration: AccelerationData) {
    val culler = FrustumCuller(acceleration)
    val builder = IndirectDrawBuilder()
    private var commandBuffer: ByteBuffer? = null
    fun cullAndBuild(frustum: Frustum?, scale: Float, camera: Vec3, lodBias: Float, indexResolver: (MeshCluster) -> Triple<Int, Int, Int>): ByteBuffer {
        builder.reset()
        val visible = culler.cull(frustum, scale, camera, lodBias)
        for (cluster in visible) {
            val (indexCount, firstIndex, baseVertex) = indexResolver(cluster)
            val passKey = (cluster.meshIndex.toLong() shl 32) or cluster.lodLevel.toLong()
            builder.add(cluster, indexCount, firstIndex, baseVertex, passKey = passKey)
        }
        return ensureCommandBuffer(builder.totalCommands()).apply { builder.encodeAll(this); flip() }
    }
    private fun ensureCommandBuffer(commands: Int): ByteBuffer {
        val required = commands * 20
        val existing = commandBuffer
        if (existing == null || existing.capacity() < required) {
            val capacity = if (required <= 0) 64 else nextPowerOfTwo(required)
            return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()).also { commandBuffer = it }
        } else existing.clear()
        return existing
    }
    private fun nextPowerOfTwo(v: Int): Int { var n = 1; while (n < v) n = n shl 1; return n }
    fun status() = "${culler.status()} cmds=${builder.totalCommands()}"
}
