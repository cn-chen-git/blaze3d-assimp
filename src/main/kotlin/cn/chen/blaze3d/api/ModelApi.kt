package cn.chen.blaze3d.api
import cn.chen.blaze3d.core.SceneData
import java.io.File
enum class ModelFeature { BINARY, TEXT, MESH, MATERIALS, TEXTURES, EMBEDDED_TEXTURES, ANIMATION, SKINNING, MORPHS, NODES, CAMERAS, LIGHTS, PHYSICS, MMD_MOTION, PLAYER_PACKAGE, SCENE_PACKAGE }
enum class ModelFormat { GLTF, GLB, FBX, OBJ, DAE, MMD, YSM, ASSIMP, OTHER }
data class ModelSupport(val id: String, val displayName: String, val extensions: Set<String>, val features: Set<ModelFeature>, val resources: Set<String> = emptySet(), val module: String = id) {
    fun supports(path: String) = ModelFormats.extension(path) in extensions
    fun resourcesIn(path: String): List<File> {
        val file = File(path)
        val base = if (file.isDirectory) file else file.parentFile ?: return emptyList()
        return base.walkTopDown().maxDepth(4).filter { it.isFile && ModelFormats.extension(it.path) in resources }.sortedBy { it.name }.toList()
    }
}
data class ModelLoadContext(val path: String, val flags: Int = 0)
data class ModelLoadResult(val path: String, val baseDir: String, val scene: SceneData, val support: ModelSupport)
interface ModelFormatProvider {
    val support: ModelSupport
    fun canLoad(path: String) = support.supports(path)
    fun load(context: ModelLoadContext): SceneData
}
object ModelFormatRegistry {
    private val providers = linkedMapOf<String, ModelFormatProvider>()
    fun register(provider: ModelFormatProvider): ModelFormatProvider { providers[provider.support.id] = provider; return provider }
    fun register(vararg providers: ModelFormatProvider) = providers.forEach { register(it) }
    fun all(): List<ModelFormatProvider> = providers.values.toList()
    fun supportAll(): List<ModelSupport> = all().map { it.support }
    fun byId(id: String): ModelFormatProvider? = providers[id]
    fun detect(path: String): ModelFormatProvider? = providers.values.firstOrNull { it.canLoad(path) }
    fun load(path: String): ModelLoadResult {
        val asset = ModelPackage.mainAsset(File(path)) ?: File(path)
        val provider = detect(asset.path) ?: throw IllegalArgumentException("model: unsupported format ${ModelFormats.extension(asset.path)}")
        return ModelLoadResult(asset.path, asset.parent ?: "", provider.load(ModelLoadContext(asset.path)), provider.support)
    }
}
class ResourceIndex private constructor(private val root: File, private val files: List<File>, private val byName: Map<String, File>) {
    fun find(name: String): File? = byName[File(name).name.lowercase()]
    fun resources() = files
    companion object {
        private val cache = object : LinkedHashMap<String, ResourceIndex>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceIndex>) = size > 16
        }
        @Synchronized fun of(path: File): ResourceIndex {
            val root = (if (path.isDirectory) path else path.parentFile ?: path).canonicalFile
            val key = "${root.path}:${root.lastModified()}"
            cache[key]?.let { return it }
            val files = if (root.isDirectory) root.walkTopDown().maxDepth(24).filter { it.isFile && ModelFormats.isAsset(it.path) }.toList() else emptyList()
            val byName = HashMap<String, File>(files.size * 2)
            for (file in files) byName.putIfAbsent(file.name.lowercase(), file)
            val index = ResourceIndex(root, files, byName)
            cache[key] = index
            return index
        }
    }
}
object ModelPackage {
    fun mainAsset(root: File): File? {
        val file = root.canonicalFile
        if (file.isFile) return if (ModelFormats.isProject(file.path)) file else null
        if (!file.isDirectory) return null
        if (ModelFormats.isYsmDirectory(file)) return file
        val assets = ResourceIndex.of(file).resources().filter { ModelFormats.isProject(it.path) }
        return assets.minWithOrNull(compareBy<File> { priority(it) }.thenBy { it.relativeTo(file).path.length }.thenBy { it.name })
    }
    fun resources(root: File): List<File> {
        val file = root.canonicalFile
        val base = if (file.isDirectory) file else file.parentFile ?: return emptyList()
        return ResourceIndex.of(base).resources().filter { ModelFormats.isResource(it.path) }.sortedBy { it.relativeTo(base).path }
    }
    private fun priority(file: File): Int {
        val name = file.name.lowercase()
        val ext = ModelFormats.extension(file.path)
        if (name == "ysm.json" || name == "main.json") return 0
        if (name == "scene.gltf" || name == "scene.glb" || name == "model.gltf" || name == "model.glb") return 1
        if (ext == "vrm" || ext == "gltf" || ext == "glb") return 2
        if (ext == "fbx" || ext == "dae" || ext == "obj") return 3
        if (ext == "pmx" || ext == "pmd") return 4
        return 8
    }
}
