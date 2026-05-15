package cn.chen.blaze3d.anim
import cn.chen.blaze3d.api.ModelFormats
import java.io.File
object AnimationLibrary {
    private val motionExts = setOf("vmd", "vpd", "bvh", "fbx", "gltf", "glb", "vrm", "md5anim")
    fun scan(root: File): List<File> {
        val base = if (root.isDirectory) root else root.parentFile ?: return emptyList()
        return base.walkTopDown().maxDepth(24).filter { it.isFile && ModelFormats.extension(it.path) in motionExts }.sortedBy { it.path }.toList()
    }
    fun isMotion(path: String) = ModelFormats.extension(path) in motionExts
}
