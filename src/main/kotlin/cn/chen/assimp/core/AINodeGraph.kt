package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
class AINodeGraph(
    val name: String,
    val transform: AIMat4,
    val meshIndices: IntArray = IntArray(0),
    val children: MutableList<AINodeGraph> = mutableListOf(),
    var parent: AINodeGraph? = null
) {
    private var lookupRoot: AINodeGraph? = null
    private var lookup: HashMap<String, AINodeGraph>? = null
    fun findNode(nodeName: String): AINodeGraph? {
        val root = if (parent == null) this else { var p: AINodeGraph = this; while (p.parent != null) p = p.parent!!; p }
        var map = root.lookup
        if (map == null || root.lookupRoot !== root) {
            map = HashMap()
            buildLookup(root, map)
            root.lookup = map; root.lookupRoot = root
        }
        return map[nodeName]
    }
    private fun buildLookup(node: AINodeGraph, out: HashMap<String, AINodeGraph>) {
        out[node.name] = node
        for (c in node.children) buildLookup(c, out)
    }
    fun globalTransform(): AIMat4 {
        var mat = transform
        var p = parent
        while (p != null) { mat = p.transform * mat; p = p.parent }
        return mat
    }
}
