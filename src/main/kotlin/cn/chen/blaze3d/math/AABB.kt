package cn.chen.blaze3d.math
class AABB(
    val min: Vec3 = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
    val max: Vec3 = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
) {
    fun expand(x: Float, y: Float, z: Float) {
        if (x < min.x) min.x = x; if (y < min.y) min.y = y; if (z < min.z) min.z = z
        if (x > max.x) max.x = x; if (y > max.y) max.y = y; if (z > max.z) max.z = z
    }
    fun expand(point: Vec3) = expand(point.x, point.y, point.z)
    fun merge(other: AABB) { expand(other.min.x, other.min.y, other.min.z); expand(other.max.x, other.max.y, other.max.z) }
    fun center() = Vec3((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f, (min.z + max.z) * 0.5f)
    fun extents() = Vec3((max.x - min.x) * 0.5f, (max.y - min.y) * 0.5f, (max.z - min.z) * 0.5f)
    fun transform(m: Mat4): AABB {
        val result = AABB()
        val mm = m.m
        for (i in 0..7) {
            val xs = if (i and 1 == 0) min.x else max.x
            val ys = if (i and 2 == 0) min.y else max.y
            val zs = if (i and 4 == 0) min.z else max.z
            val w = mm[12] * xs + mm[13] * ys + mm[14] * zs + mm[15]
            val tx = (mm[0] * xs + mm[1] * ys + mm[2] * zs + mm[3]) / w
            val ty = (mm[4] * xs + mm[5] * ys + mm[6] * zs + mm[7]) / w
            val tz = (mm[8] * xs + mm[9] * ys + mm[10] * zs + mm[11]) / w
            result.expand(tx, ty, tz)
        }
        return result
    }
    fun intersects(other: AABB) = min.x <= other.max.x && max.x >= other.min.x &&
        min.y <= other.max.y && max.y >= other.min.y && min.z <= other.max.z && max.z >= other.min.z
    fun contains(point: Vec3) = point.x in min.x..max.x && point.y in min.y..max.y && point.z in min.z..max.z
}
