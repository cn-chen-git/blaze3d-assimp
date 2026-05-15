package cn.chen.blaze3d.api
import java.io.File
object ModelFormats {
    val ASSIMP_EXTENSIONS = setOf("3d", "3ds", "3mf", "ac", "ac3d", "acc", "amf", "ase", "ask", "assbin", "b3d", "blend", "bsp", "bvh", "cob", "csm", "dae", "dxf", "enff", "fbx", "glb", "gltf", "hmp", "ifc", "ifczip", "iqm", "irr", "irrmesh", "lwo", "lws", "lxo", "md2", "md3", "md5anim", "md5camera", "md5mesh", "mdc", "mdl", "mesh", "mesh.xml", "mot", "ms3d", "ndo", "nff", "obj", "off", "ogex", "pk3", "ply", "pmx", "prj", "q3o", "q3s", "raw", "scn", "sib", "smd", "step", "stl", "stp", "ter", "uc", "vrm", "vta", "x", "x3d", "x3db", "xgl", "xml", "zae", "zgl")
    val PROJECT_EXTENSIONS = ASSIMP_EXTENSIONS + setOf("ysm", "zip", "vmd", "vpd", "json", "geo.json", "bbmodel")
    val RESOURCE_EXTENSIONS = setOf("bin", "mtl", "material", "skeleton", "skeleton.xml", "png", "jpg", "jpeg", "webp", "ktx", "ktx2", "tga", "bmp", "dds", "exr", "hdr")
    val SANDBOX_EXTENSIONS = PROJECT_EXTENSIONS + RESOURCE_EXTENSIONS
    fun extension(path: String): String {
        val name = File(path).name.lowercase()
        val compound = when {
            name.endsWith(".mesh.xml") -> "mesh.xml"
            name.endsWith(".skeleton.xml") -> "skeleton.xml"
            name.endsWith(".geo.json") -> "geo.json"
            else -> ""
        }
        return compound.ifEmpty { name.substringAfterLast('.', "") }
    }
    fun isAssimp(path: String) = extension(path) in ASSIMP_EXTENSIONS
    fun isProject(path: String) = extension(path) in PROJECT_EXTENSIONS
    fun isResource(path: String) = extension(path) in RESOURCE_EXTENSIONS
    fun isAsset(path: String) = isProject(path) || isResource(path)
    fun isYsmDirectory(file: File) = file.isDirectory && (File(file, "ysm.json").isFile || File(file, "main.json").isFile || File(file, "models/main.json").isFile)
    fun isBrowsable(file: File) = if (file.isDirectory) isYsmDirectory(file) else file.isFile && isProject(file.path)
}
