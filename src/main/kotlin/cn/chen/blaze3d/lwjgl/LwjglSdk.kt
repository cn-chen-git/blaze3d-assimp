package cn.chen.blaze3d.lwjgl
object LwjglSdk {
    const val VERSION = "3.4.1"
    val libraries = listOf("assimp", "shaderc", "stb", "vma")
    val platforms = listOf("linux", "linux-arm64", "macos", "macos-arm64", "windows", "windows-arm64")
    fun artifact(module: String) = "org.lwjgl:lwjgl-$module:$VERSION"
}
