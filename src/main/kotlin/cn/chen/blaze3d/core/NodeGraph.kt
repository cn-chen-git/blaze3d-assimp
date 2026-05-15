package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Mat4
class NodeGraph(
    val name: String,
    val transform: Mat4,
    val meshIndices: IntArray = IntArray(0),
    val children: MutableList<NodeGraph> = mutableListOf(),
    var parent: NodeGraph? = null
) {
    private var lookup: HashMap<String, NodeGraph>? = null
    fun findNode(nodeName: String): NodeGraph? {
        val root = rootOf()
        val map = root.lookup ?: HashMap<String, NodeGraph>().also { root.buildLookup(it); root.lookup = it }
        return map[nodeName]
    }
    private fun rootOf(): NodeGraph {
        var node = this
        while (true) node = node.parent ?: return node
    }
    private fun buildLookup(out: HashMap<String, NodeGraph>) {
        out[name] = this
        for (c in children) c.buildLookup(out)
    }
    fun globalTransform(): Mat4 {
        var mat = transform
        var p = parent
        while (p != null) { mat = p.transform * mat; p = p.parent }
        return mat
    }
}
