package cn.chen.assimp.math
data class AIAABB(var min: AIVec3 = AIVec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
                  var max: AIVec3 = AIVec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)) {
    fun expand(point: AIVec3) {
        min = AIVec3(minOf(min.x, point.x), minOf(min.y, point.y), minOf(min.z, point.z))
        max = AIVec3(maxOf(max.x, point.x), maxOf(max.y, point.y), maxOf(max.z, point.z))
    }
    fun merge(other: AIAABB) { expand(other.min); expand(other.max) }
    fun center() = AIVec3((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f, (min.z + max.z) * 0.5f)
    fun extents() = AIVec3((max.x - min.x) * 0.5f, (max.y - min.y) * 0.5f, (max.z - min.z) * 0.5f)
    fun transform(m: AIMat4): AIAABB {
        val corners = arrayOf(
            AIVec3(min.x, min.y, min.z), AIVec3(max.x, min.y, min.z),
            AIVec3(min.x, max.y, min.z), AIVec3(max.x, max.y, min.z),
            AIVec3(min.x, min.y, max.z), AIVec3(max.x, min.y, max.z),
            AIVec3(min.x, max.y, max.z), AIVec3(max.x, max.y, max.z)
        )
        val result = AIAABB()
        for (c in corners) result.expand(m.transformPoint(c))
        return result
    }
    fun intersects(other: AIAABB) = min.x <= other.max.x && max.x >= other.min.x &&
        min.y <= other.max.y && max.y >= other.min.y && min.z <= other.max.z && max.z >= other.min.z
    fun contains(point: AIVec3) = point.x in min.x..max.x && point.y in min.y..max.y && point.z in min.z..max.z
}
