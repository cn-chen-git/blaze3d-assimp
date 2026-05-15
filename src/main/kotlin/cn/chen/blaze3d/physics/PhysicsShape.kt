package cn.chen.blaze3d.physics
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
class PhysicsShape(val meshCount: Int, val vertexCount: Int, val indexCount: Int, val min: Vector3f, val max: Vector3f) {
    val center = Vector3f(min).add(max).mul(0.5f)
    val halfExtents = Vector3f(max).sub(min).mul(0.5f)
    fun intersects(point: Vector3f, radius: Float): Boolean {
        val x = point.x.coerceIn(min.x, max.x)
        val y = point.y.coerceIn(min.y, max.y)
        val z = point.z.coerceIn(min.z, max.z)
        val dx = point.x - x; val dy = point.y - y; val dz = point.z - z
        return dx * dx + dy * dy + dz * dz <= radius * radius
    }
    fun box(position: Vector3f, scale: Float): AABB = AABB(
        (position.x + min.x * scale).toDouble(),
        (position.y + min.y * scale).toDouble(),
        (position.z + min.z * scale).toDouble(),
        (position.x + max.x * scale).toDouble(),
        (position.y + max.y * scale).toDouble(),
        (position.z + max.z * scale).toDouble()
    )
}
