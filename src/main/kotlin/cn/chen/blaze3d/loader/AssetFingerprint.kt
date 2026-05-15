package cn.chen.blaze3d.loader
import cn.chen.blaze3d.api.ModelFormats
import java.io.File
import java.security.MessageDigest
object AssetFingerprint {
    fun of(path: String, flags: Int): String {
        val file = File(path).canonicalFile
        val md = MessageDigest.getInstance("SHA-256")
        update(md, file.path)
        update(md, flags.toString())
        if (file.isDirectory) file.walkTopDown().maxDepth(24).filter { it.isFile && ModelFormats.isAsset(it.path) }.sortedBy { it.relativeTo(file).path }.forEach { stamp(md, file, it) } else if (file.isFile) stamp(md, file.parentFile ?: file, file)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun stamp(md: MessageDigest, root: File, file: File) {
        update(md, file.relativeToOrSelf(root).path.replace('\\', '/'))
        update(md, file.length().toString())
        update(md, file.lastModified().toString())
    }
    private fun update(md: MessageDigest, text: String) = md.update(text.toByteArray(Charsets.UTF_8))
}
