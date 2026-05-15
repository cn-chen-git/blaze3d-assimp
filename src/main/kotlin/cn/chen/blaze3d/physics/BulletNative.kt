package cn.chen.blaze3d.physics
import java.io.File
object BulletNative {
    var loaded = false
        private set
    private val NATIVE_PATHS = listOf(
        "native/linux/x86_64", "native/linux/aarch64",
        "native/windows/x86_64", "native/windows/aarch64",
        "native/osx/x86_64", "native/osx/arm64")
    fun load() {
        if (loaded) return
        val mapped = System.mapLibraryName("bulletjme")
        val home = File(System.getProperty("user.home"), ".blaze3d/native").apply { mkdirs() }
        val target = File(home, mapped)
        for (dir in NATIVE_PATHS) {
            val src = File(dir, mapped)
            if (!src.isFile) continue
            runCatching {
                src.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                System.load(target.absolutePath)
                loaded = true
                return
            }
        }
        val jar = File("lib/$mapped")
        if (jar.isFile) {
            runCatching {
                jar.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                System.load(target.absolutePath)
                loaded = true
                return
            }
        }
        runCatching { System.loadLibrary("bulletjme"); loaded = true }
    }
}
