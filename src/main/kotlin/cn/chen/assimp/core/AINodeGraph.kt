package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
class AINodeGraph(
    val name: String,
    val transform: AIMat4,
    val meshIndices: IntArray = IntArray(0),
    val children: MutableList<AINodeGraph> = mutableListOf(),
    var parent: AINodeGraph? = null
) {
    fun findNode(nodeName: String): AINodeGraph? {
        if (name == nodeName) return this
        for (child in children) {
            val found = child.findNode(nodeName)
            if (found != null) return found
        }
        return null
    }
    fun globalTransform(): AIMat4 {
        var mat = transform
        var p = parent
        while (p != null) { mat = p.transform * mat; p = p.parent }
        return mat
    }
}
