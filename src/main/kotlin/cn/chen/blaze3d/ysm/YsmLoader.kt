package cn.chen.blaze3d.ysm
import cn.chen.blaze3d.api.ModelFormats
import cn.chen.blaze3d.core.AnimBehaviour
import cn.chen.blaze3d.core.AnimChannel
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.BoneInfo
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.core.Vertex
import cn.chen.blaze3d.material.AlphaMode
import cn.chen.blaze3d.material.EmbeddedTex
import cn.chen.blaze3d.material.Material
import cn.chen.blaze3d.material.TexInfo
import cn.chen.blaze3d.material.TexType
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
import cn.chen.blaze3d.math.Vec4
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.LinkedHashMap
import java.util.Random
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
object YsmLoader {
    private const val HEAD = 0x59534750
    private const val VERSION = 1
    private const val VERSION_II = 2
    private const val SCALE = 0.0625f
    fun canLoad(path: String): Boolean {
        val file = File(path)
        val ext = ModelFormats.extension(path)
        return ext == "ysm" || ext == "zip" || ModelFormats.isYsmDirectory(file)
    }
    fun load(path: String): SceneData {
        val files = readFiles(File(path))
        if (files.isEmpty()) throw IllegalArgumentException("ysm: no readable model files")
        return buildScene(files)
    }
    private fun readFiles(file: File): Map<String, ByteArray> {
        if (file.isDirectory) return readFolder(file)
        return when (ModelFormats.extension(file.path)) {
            "ysm" -> readYsm(file.readBytes())
            "zip" -> readZip(file.readBytes())
            else -> emptyMap()
        }
    }
    private fun readFolder(root: File): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        root.walkTopDown().maxDepth(16).filter { it.isFile }.forEach { out[root.toPath().relativize(it.toPath()).toString().replace('\\', '/')] = it.readBytes() }
        return out
    }
    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (!name.contains("..")) out[name] = stream.readBytes()
                }
                stream.closeEntry()
            }
        }
        return out
    }
    private fun readYsm(bytes: ByteArray): Map<String, ByteArray> {
        if (bytes.size < 24 || readInt(bytes, 0) != HEAD) return emptyMap()
        val version = readInt(bytes, 4)
        if (version != VERSION && version != VERSION_II) return emptyMap()
        val body = bytes.copyOfRange(24, bytes.size)
        val md5 = MessageDigest.getInstance("MD5").digest(body)
        if (!md5.contentEquals(bytes.copyOfRange(8, 24))) return emptyMap()
        val out = LinkedHashMap<String, ByteArray>()
        val input = ByteArrayInputStream(body)
        while (input.available() > 0) {
            val entry = if (version == VERSION) readYsmV1(input) else readYsmV2(input)
            out[entry.first.replace('\\', '/')] = entry.second
        }
        return out
    }
    private fun readYsmV1(input: ByteArrayInputStream): Pair<String, ByteArray> {
        val name = readString(input)
        val size = readInt(input)
        val key = input.readBytesExact(16)
        val iv = input.readBytesExact(16)
        val data = input.readBytesExact(size)
        return name to inflate(aes(SecretKeySpec(key, "AES"), IvParameterSpec(iv), data))
    }
    private fun readYsmV2(input: ByteArrayInputStream): Pair<String, ByteArray> {
        val name = String(Base64.getDecoder().decode(readString(input)), StandardCharsets.UTF_8)
        val fileSize = readInt(input)
        val keySize = readInt(input)
        val cipherKey = input.readBytesExact(keySize)
        val iv = input.readBytesExact(16)
        val data = input.readBytesExact(fileSize)
        val ivSpec = IvParameterSpec(iv)
        val secret = SecretKeySpec(keyFromMd5(data), "AES")
        val key = SecretKeySpec(aes(secret, ivSpec, cipherKey), "AES")
        return name to inflate(aes(key, ivSpec, data))
    }
    private fun buildScene(files: Map<String, ByteArray>): SceneData {
        val manifestPath = files.keys.firstOrNull { it.endsWith("ysm.json", true) }
        val base = manifestPath?.substringBeforeLast('/', "") ?: ""
        val modelPaths = linkedSetOf<String>()
        val animPaths = linkedSetOf<String>()
        val texturePaths = linkedSetOf<String>()
        if (manifestPath != null) {
            val manifestBytes = files[manifestPath] ?: throw IllegalArgumentException("ysm: missing manifest")
            val manifest = YsmCodec.decodeManifest(manifestBytes.toString(StandardCharsets.UTF_8))
            val player = manifest.files?.player
            player?.model?.values?.forEach { modelPaths.add(resolve(base, it)) }
            player?.animation?.values?.forEach { animPaths.add(resolve(base, it)) }
            player?.texture?.forEach { texturePaths.add(resolve(base, it)) }
        }
        if (modelPaths.isEmpty()) {
            findByNames(files, "main.json", "models/main.json")?.let { modelPaths.add(it) }
            findByNames(files, "arm.json", "models/arm.json")?.let { modelPaths.add(it) }
        }
        if (animPaths.isEmpty()) files.keys.filter { it.endsWith(".animation.json", true) || it.contains("/animations/", true) && it.endsWith(".json", true) }.sorted().forEach { animPaths.add(it) }
        if (texturePaths.isEmpty()) files.keys.filter { isTexture(it) }.sorted().forEach { texturePaths.add(it) }
        if (modelPaths.isEmpty()) throw IllegalArgumentException("ysm: missing model json")
        val embedded = texturePaths.mapNotNull { files[it]?.let { data -> EmbeddedTex(data.size, 0, data, File(it).extension.lowercase()) } }
        val materials = if (embedded.isEmpty()) listOf(Material(name = "ysm_default", doubleSided = true)) else texturePaths.mapIndexed { i, p -> Material(name = File(p).nameWithoutExtension, alphaMode = AlphaMode.BLEND, doubleSided = true, textures = mutableMapOf(TexType.ALBEDO to TexInfo("*$i", TexType.ALBEDO))) }
        val model = YsmBuildModel(materials)
        for (path in modelPaths) files[path]?.let { parseGeometry(path, it.toString(StandardCharsets.UTF_8), model) }
        val root = buildNodeGraph(model)
        val animations = animPaths.mapNotNull { files[it]?.let { data -> parseAnimations(File(it).nameWithoutExtension, data.toString(StandardCharsets.UTF_8), model) } }.flatten()
        val skeleton = if (model.boneInfos.isEmpty()) null else BonePose(model.boneInfos, Mat4.identity())
        return SceneData(model.meshes(), materials, animations, root, skeleton, embedded)
    }
    private fun parseGeometry(path: String, text: String, model: YsmBuildModel) {
        val decoded = YsmCodec.decodeGeometry(text)
        for (geo in decoded.geometries) {
            val texW = geo.description?.texture_width ?: 64f
            val texH = geo.description?.texture_height ?: 64f
            for (bone in geo.bones) {
                if (bone.name.isEmpty()) continue
                val parent = bone.parent.ifEmpty { null }
                val pivot = YsmCodec.float3(bone.pivot)
                val rotation = YsmCodec.float3(bone.rotation)
                val def = model.bones.getOrPut(bone.name) { YsmBoneDef(bone.name, parent, pivot, rotation) }
                def.parent = parent
                def.pivot = pivot
                def.rotation = rotation
                for (cube in bone.cubes) appendCubeTyped(path, bone.name, cube, texW, texH, model)
            }
        }
    }
    private fun appendCubeTyped(modelPath: String, boneName: String, cube: YsmCube, texW: Float, texH: Float, model: YsmBuildModel) {
        val origin = YsmCodec.float3(cube.origin)
        val size = YsmCodec.float3(cube.size, fallback = 1f)
        val pivot = YsmCodec.float3(cube.pivot)
        val rotation = YsmCodec.float3(cube.rotation)
        val inflate = cube.inflate
        val material = cube.material.toInt().coerceIn(0, model.materials.lastIndex.coerceAtLeast(0))
        val builder = model.meshBuilder("$modelPath:$boneName:$material", material)
        val x0 = (origin[0] - inflate) * SCALE; val y0 = (origin[1] - inflate) * SCALE; val z0 = (origin[2] - inflate) * SCALE
        val x1 = (origin[0] + size[0] + inflate) * SCALE; val y1 = (origin[1] + size[1] + inflate) * SCALE; val z1 = (origin[2] + size[2] + inflate) * SCALE
        val points = arrayOf(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0), Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1))
        if (rotation[0] != 0f || rotation[1] != 0f || rotation[2] != 0f) rotatePoints(points, Vec3(pivot[0] * SCALE, pivot[1] * SCALE, pivot[2] * SCALE), quat(rotation))
        val uv = cube.uv
        appendFace(builder, boneName, points, intArrayOf(1, 0, 3, 2), Vec3(0f, 0f, -1f), faceUv(uv, "north", texW, texH, 0f, 0f, size[0], size[1]))
        appendFace(builder, boneName, points, intArrayOf(4, 5, 6, 7), Vec3(0f, 0f, 1f), faceUv(uv, "south", texW, texH, 0f, 0f, size[0], size[1]))
        appendFace(builder, boneName, points, intArrayOf(0, 4, 7, 3), Vec3(-1f, 0f, 0f), faceUv(uv, "west", texW, texH, 0f, 0f, size[2], size[1]))
        appendFace(builder, boneName, points, intArrayOf(5, 1, 2, 6), Vec3(1f, 0f, 0f), faceUv(uv, "east", texW, texH, 0f, 0f, size[2], size[1]))
        appendFace(builder, boneName, points, intArrayOf(3, 7, 6, 2), Vec3(0f, 1f, 0f), faceUv(uv, "up", texW, texH, 0f, 0f, size[0], size[2]))
        appendFace(builder, boneName, points, intArrayOf(0, 1, 5, 4), Vec3(0f, -1f, 0f), faceUv(uv, "down", texW, texH, 0f, 0f, size[0], size[2]))
    }
    private fun appendFace(builder: YsmMeshBuilder, boneName: String, points: Array<Vec3>, ids: IntArray, normal: Vec3, uv: FloatArray) {
        val tangent = if (normal.y != 0f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
        val bitangent = normal.cross(tangent).normalize()
        val uvs = arrayOf(Vec3(uv[0], uv[1], 0f), Vec3(uv[2], uv[1], 0f), Vec3(uv[2], uv[3], 0f), Vec3(uv[0], uv[3], 0f))
        val base = builder.vertices.size
        for (i in 0..3) builder.vertices.add(Vertex(points[ids[i]], normal, tangent, bitangent, uvs[i], Vec3(), Vec4(1f, 1f, 1f, 1f), intArrayOf(builder.model.boneIndex(boneName), -1, -1, -1), floatArrayOf(1f, 0f, 0f, 0f)))
        builder.indices.add(base); builder.indices.add(base + 1); builder.indices.add(base + 2); builder.indices.add(base); builder.indices.add(base + 2); builder.indices.add(base + 3)
    }
    private fun parseAnimations(baseName: String, text: String, model: YsmBuildModel): List<AnimClip> {
        val decoded = YsmCodec.decodeAnimation(text)
        return decoded.animations.map { (name, animation) ->
            val computedDuration = animation.bones.values.flatMap { listOf(YsmCodec.keyframeTimes(it.position).maxOrNull() ?: 0.0, YsmCodec.keyframeTimes(it.rotation).maxOrNull() ?: 0.0, YsmCodec.keyframeTimes(it.scale).maxOrNull() ?: 0.0) }.maxOrNull() ?: 0.0
            val duration = (if (animation.animation_length > 0f) animation.animation_length.toDouble() else computedDuration).coerceAtLeast(0.05)
            val channels = animation.bones.mapNotNull { (boneName, bone) ->
                val def = model.bones[boneName] ?: return@mapNotNull null
                val pos = animVecKeys(bone.position, floatArrayOf((def.pivot[0] - parentPivot(model, def)[0]) * SCALE, (def.pivot[1] - parentPivot(model, def)[1]) * SCALE, (def.pivot[2] - parentPivot(model, def)[2]) * SCALE), true)
                val rot = animQuatKeys(bone.rotation, def.rotation)
                val scale = animVecKeys(bone.scale, floatArrayOf(1f, 1f, 1f), false)
                AnimChannel(boneName, pos.first, pos.second, rot.first, rot.second, scale.first, scale.second, AnimBehaviour.DEFAULT, AnimBehaviour.DEFAULT)
            }
            AnimClip("$baseName:$name", duration, 1.0, channels)
        }
    }
    private fun buildNodeGraph(model: YsmBuildModel): NodeGraph {
        val root = NodeGraph("YSMRoot", Mat4.identity())
        fun create(def: YsmBoneDef): NodeGraph {
            model.nodes[def.name]?.let { return it }
            val parentDef = def.parent?.let { model.bones[it] }
            val parentNode = parentDef?.let { create(it) } ?: root
            val local = localTransform(model, def)
            val node = NodeGraph(def.name, local, IntArray(0), mutableListOf(), parentNode)
            parentNode.children.add(node)
            model.nodes[def.name] = node
            return node
        }
        model.bones.values.forEach { create(it) }
        model.boneInfos.clear()
        for ((name, node) in model.nodes) model.boneInfos[name] = BoneInfo(name, node.globalTransform().inverse(), model.boneIndex(name))
        val meshRoot = NodeGraph("YSMMeshes", Mat4.identity(), IntArray(model.builders.size) { it }, mutableListOf(), root)
        root.children.add(meshRoot)
        return root
    }
    private fun localTransform(model: YsmBuildModel, def: YsmBoneDef): Mat4 {
        val parentPivot = parentPivot(model, def)
        val q = quat(def.rotation)
        val out = Mat4()
        Mat4.trsInto((def.pivot[0] - parentPivot[0]) * SCALE, (def.pivot[1] - parentPivot[1]) * SCALE, (def.pivot[2] - parentPivot[2]) * SCALE, q.x, q.y, q.z, q.w, 1f, 1f, 1f, out)
        return out
    }
    private fun parentPivot(model: YsmBuildModel, def: YsmBoneDef): FloatArray = def.parent?.let { model.bones[it]?.pivot } ?: FloatArray(3)
    private fun animVecKeys(element: JsonElement?, fallback: FloatArray, position: Boolean): Pair<DoubleArray, FloatArray> {
        val keys = numericKeyMap(element) { arr -> floatArrayOf((arr.getOrElse(0) { 0f } * if (position) SCALE else 1f) + fallback[0], (arr.getOrElse(1) { 0f } * if (position) SCALE else 1f) + fallback[1], (arr.getOrElse(2) { 0f } * if (position) SCALE else 1f) + fallback[2]) }
        if (keys.isEmpty()) return doubleArrayOf(0.0) to fallback.copyOf()
        return keys.keys.toDoubleArray() to keys.values.flatMap { it.asIterable() }.toFloatArray()
    }
    private fun animQuatKeys(element: JsonElement?, fallbackEuler: FloatArray): Pair<DoubleArray, FloatArray> {
        val fallback = quat(fallbackEuler)
        val keys = numericKeyMap(element) { quat(it) }
        if (keys.isEmpty()) return doubleArrayOf(0.0) to floatArrayOf(fallback.x, fallback.y, fallback.z, fallback.w)
        return keys.keys.toDoubleArray() to keys.values.flatMap { listOf(it.x, it.y, it.z, it.w) }.toFloatArray()
    }
    private fun <T> numericKeyMap(element: JsonElement?, convert: (FloatArray) -> T): LinkedHashMap<Double, T> {
        val out = LinkedHashMap<Double, T>()
        when (element) {
            is JsonArray -> out[0.0] = convert(element.floatArray())
            is JsonObject -> element.keys.mapNotNull { it.toDoubleOrNull() }.sorted().forEach { t -> vectorElement(element.entries.firstOrNull { it.key.toDoubleOrNull() == t }?.value)?.let { out[t] = convert(it) } }
            is JsonPrimitive -> element.jsonPrimitive.doubleOrNull?.toFloat()?.let { out[0.0] = convert(floatArrayOf(it, it, it)) }
            else -> {}
        }
        return out
    }
    private fun faceUv(element: JsonElement?, face: String, texW: Float, texH: Float, x: Float, y: Float, w: Float, h: Float): FloatArray {
        val faceObj = (element as? JsonObject)?.obj(face)
        val uv = faceObj?.floatArray("uv", 2, 0f)
        val size = faceObj?.floatArray("uv_size", 2, 0f)
        val base = if (element is JsonArray) element.floatArray() else null
        val u0 = uv?.get(0) ?: base?.getOrNull(0) ?: x
        val v0 = uv?.get(1) ?: base?.getOrNull(1) ?: y
        val uw = size?.get(0) ?: w
        val vh = size?.get(1) ?: h
        return floatArrayOf(u0 / texW, v0 / texH, (u0 + uw) / texW, (v0 + vh) / texH)
    }
    private fun rotatePoints(points: Array<Vec3>, pivot: Vec3, q: Quat) {
        for (p in points) {
            val x = p.x - pivot.x; val y = p.y - pivot.y; val z = p.z - pivot.z
            val ix = q.w * x + q.y * z - q.z * y; val iy = q.w * y + q.z * x - q.x * z; val iz = q.w * z + q.x * y - q.y * x; val iw = -q.x * x - q.y * y - q.z * z
            p.x = ix * q.w + iw * -q.x + iy * -q.z - iz * -q.y + pivot.x
            p.y = iy * q.w + iw * -q.y + iz * -q.x - ix * -q.z + pivot.y
            p.z = iz * q.w + iw * -q.z + ix * -q.y - iy * -q.x + pivot.z
        }
    }
    private fun quat(euler: FloatArray): Quat {
        val x = euler.getOrElse(0) { 0f } * PI.toFloat() / 360f; val y = euler.getOrElse(1) { 0f } * PI.toFloat() / 360f; val z = euler.getOrElse(2) { 0f } * PI.toFloat() / 360f
        val sx = sin(x); val cx = cos(x); val sy = sin(y); val cy = cos(y); val sz = sin(z); val cz = cos(z)
        return Quat(sx * cy * cz + cx * sy * sz, cx * sy * cz - sx * cy * sz, cx * cy * sz + sx * sy * cz, cx * cy * cz - sx * sy * sz).normalize()
    }
    private fun aes(key: SecretKey, iv: IvParameterSpec, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(data)
    }
    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        inflater.setInput(data)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
    private fun keyFromMd5(data: ByteArray): ByteArray {
        val md5 = MessageDigest.getInstance("MD5").digest(data)
        val key = ByteArray(16)
        Random(toLong(md5)).nextBytes(key)
        return key
    }
    private fun toLong(bytes: ByteArray): Long {
        var value = 0L
        for (b in bytes) value = (value shl 8) + (b.toInt() and 255)
        return value
    }
    private fun readString(input: ByteArrayInputStream): String = String(input.readBytesExact(readInt(input)), StandardCharsets.UTF_8)
    private fun readInt(input: ByteArrayInputStream): Int = readInt(input.readBytesExact(4), 0)
    private fun readInt(bytes: ByteArray, start: Int): Int = ((bytes[start].toInt() and 255) shl 24) or ((bytes[start + 1].toInt() and 255) shl 16) or ((bytes[start + 2].toInt() and 255) shl 8) or (bytes[start + 3].toInt() and 255)
    private fun ByteArrayInputStream.readBytesExact(size: Int): ByteArray {
        val out = ByteArray(size)
        if (read(out) != size) throw IllegalArgumentException("ysm: truncated data")
        return out
    }
    private fun resolve(base: String, path: String): String = if (base.isBlank()) path.replace('\\', '/') else "$base/${path.replace('\\', '/')}"
    private fun findByNames(files: Map<String, ByteArray>, vararg names: String): String? = names.firstNotNullOfOrNull { name -> files.keys.firstOrNull { it.equals(name, true) || it.endsWith("/$name", true) } }
    private fun isTexture(path: String): Boolean = File(path).extension.lowercase() in setOf("png", "jpg", "jpeg", "bmp", "tga", "webp")
    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.floatArray(name: String, size: Int, fallback: Float): FloatArray = this[name]?.floatArray()?.let { if (it.size >= size) it else FloatArray(size) { i -> it.getOrElse(i) { fallback } } } ?: FloatArray(size) { fallback }
    private fun vectorElement(element: JsonElement?): FloatArray? = when (element) {
        is JsonArray -> element.floatArray()
        is JsonPrimitive -> element.doubleOrNull?.toFloat()?.let { floatArrayOf(it, it, it) }
        is JsonObject -> (element["post"] ?: element["pre"] ?: element["vector"])?.floatArray()
        else -> null
    }
    private fun JsonElement.floatArray(): FloatArray = when (this) {
        is JsonArray -> FloatArray(size) { this[it].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f }
        is JsonPrimitive -> doubleOrNull?.toFloat()?.let { floatArrayOf(it, it, it) } ?: FloatArray(0)
        else -> FloatArray(0)
    }
}
private class YsmBuildModel(val materials: List<Material>) {
    val bones = LinkedHashMap<String, YsmBoneDef>()
    val nodes = LinkedHashMap<String, NodeGraph>()
    val boneInfos = LinkedHashMap<String, BoneInfo>()
    val builders = LinkedHashMap<String, YsmMeshBuilder>()
    private val boneIndices = LinkedHashMap<String, Int>()
    fun boneIndex(name: String): Int = boneIndices.getOrPut(name) { boneIndices.size }
    fun meshBuilder(name: String, material: Int): YsmMeshBuilder = builders.getOrPut(name) { YsmMeshBuilder(this, name, material) }
    fun meshes(): List<MeshData> = builders.values.map { it.mesh(boneInfos.values.toList()) }
}
private class YsmMeshBuilder(val model: YsmBuildModel, val name: String, val material: Int) {
    val vertices = ArrayList<Vertex>()
    val indices = ArrayList<Int>()
    fun mesh(bones: List<BoneInfo>): MeshData = MeshData(name, vertices, indices.toIntArray(), material, bones)
}
private class YsmBoneDef(val name: String, var parent: String?, var pivot: FloatArray, var rotation: FloatArray)
