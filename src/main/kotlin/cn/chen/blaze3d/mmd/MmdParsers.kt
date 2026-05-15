package cn.chen.blaze3d.mmd
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
object MmdVmdParser {
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    fun parse(file: File): MmdMotion {
        val r = MmdBinary(file.readBytes())
        val magic = r.fixedString(30, SHIFT_JIS)
        if (!magic.startsWith("Vocaloid Motion Data")) throw IllegalArgumentException("mmd.vmd.invalid")
        val name = r.fixedString(20, SHIFT_JIS)
        val bones = ArrayList<MmdBoneFrame>()
        val curves = ArrayList<MmdBoneFrameCurve>()
        repeat(r.i32().coerceAtLeast(0)) {
            val bone = r.fixedString(15, SHIFT_JIS)
            val frame = r.i32()
            val pos = Vec3(r.f32(), r.f32(), r.f32())
            val rot = Quat(r.f32(), r.f32(), r.f32(), r.f32()).normalize()
            val curve = r.bytes(64)
            bones.add(MmdBoneFrame(bone, frame, pos, rot))
            curves.add(MmdBoneFrameCurve(bone, frame, MmdBoneInterpolation(bezier(curve, 0), bezier(curve, 4), bezier(curve, 8), bezier(curve, 12))))
        }
        val morphs = ArrayList<MmdMorphFrame>()
        if (r.remaining >= 4) repeat(r.i32().coerceAtLeast(0)) {
            morphs.add(MmdMorphFrame(r.fixedString(15, SHIFT_JIS), r.i32(), r.f32()))
        }
        val cameras = ArrayList<MmdCameraFrame>()
        if (r.remaining >= 4) repeat(r.i32().coerceAtLeast(0)) {
            val frame = r.i32(); val dist = r.f32(); val pos = Vec3(r.f32(), r.f32(), r.f32()); val rot = Vec3(r.f32(), r.f32(), r.f32())
            r.skip(24)
            val fov = if (r.remaining >= 5) { val v = r.i32(); r.skip(1); v } else 45
            cameras.add(MmdCameraFrame(frame, dist, pos, rot, fov))
        }
        val lights = ArrayList<MmdLightFrame>()
        if (r.remaining >= 4) repeat(r.i32().coerceAtLeast(0)) {
            lights.add(MmdLightFrame(r.i32(), Vec3(r.f32(), r.f32(), r.f32()), Vec3(r.f32(), r.f32(), r.f32())))
        }
        val shadows = ArrayList<MmdShadowFrame>()
        if (r.remaining >= 4) repeat(r.i32().coerceAtLeast(0)) {
            shadows.add(MmdShadowFrame(r.i32(), r.u8(), r.f32()))
        }
        val ikFrames = ArrayList<MmdIkFrame>()
        if (r.remaining >= 4) repeat(r.i32().coerceAtLeast(0)) {
            val frame = r.i32(); val show = r.u8() != 0
            val iks = ArrayList<MmdIkEnable>()
            repeat(r.i32().coerceAtLeast(0)) { iks.add(MmdIkEnable(r.fixedString(20, SHIFT_JIS), r.u8() != 0)) }
            ikFrames.add(MmdIkFrame(frame, show, iks))
        }
        return MmdMotion(name.ifBlank { file.nameWithoutExtension }, bones, morphs, cameras, lights, shadows, ikFrames, curves)
    }
    private fun bezier(v: ByteArray, offset: Int) = MmdBezier(v.getOrElse(offset) { 20 }.toInt() and 0xFF, v.getOrElse(offset + 1) { 20 }.toInt() and 0xFF, v.getOrElse(offset + 2) { 107 }.toInt() and 0xFF, v.getOrElse(offset + 3) { 107 }.toInt() and 0xFF)
}
object MmdVpdParser {
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    fun parse(file: File): MmdPose {
        val text = file.readText(SHIFT_JIS)
        val bones = ArrayList<MmdBoneFrame>()
        val regex = Regex("Bone\\d+\\s*\\{\\s*([^\\r\\n]+)\\s*\\n\\s*([^;]+);\\s*\\n\\s*([^;]+);", RegexOption.MULTILINE)
        for (m in regex.findAll(text)) {
            val name = m.groupValues[1].trim()
            val p = floats(m.groupValues[2], 3)
            val q = floats(m.groupValues[3], 4)
            bones.add(MmdBoneFrame(name, 0, Vec3(p[0], p[1], p[2]), Quat(q[0], q[1], q[2], q[3]).normalize()))
        }
        return MmdPose(file.nameWithoutExtension, bones, emptyList())
    }
    private fun floats(value: String, count: Int): FloatArray {
        val parts = value.split(",", " ", "\t").mapNotNull { it.trim().toFloatOrNull() }
        return FloatArray(count) { parts.getOrElse(it) { if (it == 3) 1f else 0f } }
    }
}
object MmdModelMetadataParser {
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    fun parse(path: String): List<MmdMaterialMeta> = parseModel(path)?.materials ?: emptyList()
    fun parseModel(path: String): MmdModelMeta? = when (MmdFormatDetector.detect(path)) {
        MmdFormat.PMX -> parsePmx(File(path))
        MmdFormat.PMD -> parsePmd(File(path))
        else -> null
    }
    private fun parsePmx(file: File): MmdModelMeta? {
        val p = PmxReader(file)
        return p.parse()
    }
    private fun parsePmd(file: File): MmdModelMeta? {
        val p = PmdReader(file)
        return p.parse()
    }
}
private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
private class PmxReader(file: File) {
    private val r = MmdBinary(file.readBytes())
    private var encoding: Charset = StandardCharsets.UTF_8
    private var extraUv = 0
    private var vertexIndex = 4
    private var textureIndex = 4
    private var materialIndex = 4
    private var boneIndex = 4
    private var morphIndex = 4
    private var rigidIndex = 4
    fun parse(): MmdModelMeta? {
        if (r.fixedString(4, StandardCharsets.US_ASCII) != "PMX ") return null
        r.f32()
        val header = IntArray(r.u8()) { r.u8() }
        encoding = if (header.getOrElse(0) { 1 } == 0) StandardCharsets.UTF_16LE else StandardCharsets.UTF_8
        extraUv = header.getOrElse(1) { 0 }; vertexIndex = header.getOrElse(2) { 4 }; textureIndex = header.getOrElse(3) { 4 }; materialIndex = header.getOrElse(4) { 4 }; boneIndex = header.getOrElse(5) { 4 }; morphIndex = header.getOrElse(6) { 4 }; rigidIndex = header.getOrElse(7) { 4 }
        val name = r.pmxText(encoding); val englishName = r.pmxText(encoding); val comment = r.pmxText(encoding); val englishComment = r.pmxText(encoding)
        repeat(r.i32().coerceAtLeast(0)) { skipVertex() }
        r.skip(r.i32().coerceAtLeast(0) * vertexIndex)
        repeat(r.i32().coerceAtLeast(0)) { r.pmxText(encoding); r.pmxText(encoding); r.pmxText(encoding); r.pmxText(encoding); repeat(r.i32().coerceAtLeast(0)) { r.index(boneIndex) } }
        val textures = List(r.i32().coerceAtLeast(0)) { r.pmxText(encoding) }
        val materials = readMaterials(textures)
        val bones = readBones()
        val morphs = readMorphs()
        skipFrames()
        val bodies = readRigidBodies()
        val joints = readJoints()
        return complete(MmdModelMeta(MmdFormat.PMX, name, englishName, comment, englishComment, materials, bones, morphs, bodies, joints))
    }
    private fun readMaterials(textures: List<String>): List<MmdMaterialMeta> {
        val materials = ArrayList<MmdMaterialMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.pmxText(encoding); r.pmxText(encoding)
            r.skip(4 * 4 + 3 * 4 + 4 + 3 * 4)
            val flags = r.u8(); val edgeColor = floatArrayOf(r.f32(), r.f32(), r.f32(), r.f32()); val edgeSize = r.f32()
            r.index(textureIndex)
            val sphere = r.index(textureIndex); val sphereMode = r.u8().toString(); val toonFlag = r.u8()
            val toon = if (toonFlag == 0) textures.getOrElse(r.index(textureIndex)) { "" } else "toon${r.u8().toString().padStart(2, '0')}.bmp"
            r.pmxText(encoding); r.skip(4)
            materials.add(MmdMaterialMeta(name, toon, textures.getOrElse(sphere) { "" }, sphereMode, edgeColor, edgeSize, flags and 1 != 0))
        }
        return materials
    }
    private fun readBones(): List<MmdBoneMeta> {
        val out = ArrayList<MmdBoneMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.pmxText(encoding); val english = r.pmxText(encoding)
            val pos = Vec3(r.f32(), r.f32(), r.f32())
            val parent = r.index(boneIndex); val layer = r.i32(); val flags = r.u16()
            var target = -1
            if (flags and 1 != 0) target = r.index(boneIndex) else r.skip(3 * 4)
            if (flags and 0x100 != 0 || flags and 0x200 != 0) { r.index(boneIndex); r.f32() }
            if (flags and 0x400 != 0) r.skip(3 * 4)
            if (flags and 0x800 != 0) r.skip(3 * 4)
            if (flags and 0x2000 != 0) r.skip(4)
            var ik: MmdIkMeta? = null
            if (flags and 0x20 != 0) {
                val ikTarget = r.index(boneIndex); val loops = r.i32(); val limit = r.f32()
                val links = ArrayList<MmdIkLinkMeta>()
                repeat(r.i32().coerceAtLeast(0)) {
                    val idx = r.index(boneIndex); val limited = r.u8() != 0
                    val min = if (limited) Vec3(r.f32(), r.f32(), r.f32()) else Vec3()
                    val max = if (limited) Vec3(r.f32(), r.f32(), r.f32()) else Vec3()
                    links.add(MmdIkLinkMeta(idx, limited, min, max))
                }
                ik = MmdIkMeta(ikTarget, loops, limit, links)
            }
            out.add(MmdBoneMeta(name, english, parent, layer, flags, pos, target, ik))
        }
        return out
    }
    private fun readMorphs(): List<MmdMorphMeta> {
        val out = ArrayList<MmdMorphMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.pmxText(encoding); val english = r.pmxText(encoding); val category = r.u8(); val type = r.u8(); val count = r.i32().coerceAtLeast(0)
            repeat(count) { skipMorphOffset(type) }
            out.add(MmdMorphMeta(name, english, category, type, count))
        }
        return out
    }
    private fun skipMorphOffset(type: Int) {
        when (type) {
            0 -> r.skip(morphIndex + 4)
            1 -> r.skip(vertexIndex + 3 * 4)
            2 -> r.skip(boneIndex + 3 * 4 + 4 * 4)
            3, 4, 5, 6, 7 -> r.skip(4 + 4 * 4)
            8 -> r.skip(materialIndex + 1 + 4 * 4 + 3 * 4 + 4 + 3 * 4 + 4 * 4 + 4 + 4 + 4 * 4 + 4 * 4 + 4 * 4)
            9 -> r.skip(morphIndex + 4)
            10 -> r.skip(rigidIndex + 4)
            11 -> r.skip(4)
            else -> {}
        }
    }
    private fun skipFrames() {
        repeat(r.i32().coerceAtLeast(0)) { r.pmxText(encoding); r.pmxText(encoding); val special = r.u8(); repeat(r.i32().coerceAtLeast(0)) { val type = r.u8(); if (special < 0) r.skip(0); if (type == 0) r.index(boneIndex) else r.index(morphIndex) } }
    }
    private fun readRigidBodies(): List<MmdRigidBodyMeta> {
        val out = ArrayList<MmdRigidBodyMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.pmxText(encoding); val english = r.pmxText(encoding); val bone = r.index(boneIndex); val group = r.u8(); val mask = r.u16(); val shape = r.u8()
            val size = Vec3(r.f32(), r.f32(), r.f32()); val pos = Vec3(r.f32(), r.f32(), r.f32()); val rot = Vec3(r.f32(), r.f32(), r.f32()); val mass = r.f32()
            r.skip(4 * 4)
            out.add(MmdRigidBodyMeta(name, english, bone, group, mask, shape, size, pos, rot, mass, r.u8()))
        }
        return out
    }
    private fun readJoints(): List<MmdJointMeta> {
        val out = ArrayList<MmdJointMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.pmxText(encoding); val english = r.pmxText(encoding); val type = r.u8(); val a = r.index(rigidIndex); val b = r.index(rigidIndex)
            val pos = Vec3(r.f32(), r.f32(), r.f32()); val rot = Vec3(r.f32(), r.f32(), r.f32())
            r.skip(3 * 4 + 3 * 4 + 3 * 4 + 3 * 4 + 3 * 4 + 3 * 4)
            out.add(MmdJointMeta(name, english, type, a, b, pos, rot))
        }
        return out
    }
    private fun skipVertex() {
        r.skip(3 * 4 + 3 * 4 + 2 * 4 + extraUv * 4 * 4)
        when (r.u8()) {
            0 -> r.skip(boneIndex)
            1 -> r.skip(boneIndex * 2 + 4)
            2 -> r.skip(boneIndex * 4 + 4 * 4)
            3 -> r.skip(boneIndex * 2 + 4 * 3)
            4 -> r.skip(boneIndex * 4 + 4 * 4 + 4 * 3)
        }
        r.skip(4)
    }
}
private class PmdReader(file: File) {
    private val r = MmdBinary(file.readBytes())
    fun parse(): MmdModelMeta? {
        if (r.fixedString(3, StandardCharsets.US_ASCII) != "Pmd") return null
        r.f32()
        val name = r.fixedString(20, SHIFT_JIS); val comment = r.fixedString(256, SHIFT_JIS)
        r.skip(r.i32().coerceAtLeast(0) * 38)
        r.skip(r.i32().coerceAtLeast(0) * 2)
        val materials = readMaterials()
        val bones = readBones()
        val iks = readIkTargets(bones)
        val morphs = readMorphs()
        skipFramesAndToon()
        val english = readEnglishNames(bones.size, morphs.size)
        val bodies = readRigidBodies()
        val joints = readJoints()
        val mergedBones = bones.mapIndexed { i, b -> if (iks.containsKey(i)) b.copy(ik = iks[i]) else b }
        return complete(MmdModelMeta(MmdFormat.PMD, name, english.first, comment, english.second, materials, mergedBones, morphs, bodies, joints))
    }
    private fun readMaterials(): List<MmdMaterialMeta> {
        val out = ArrayList<MmdMaterialMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            r.skip(4 * 4 + 4 + 3 * 4)
            val alpha = r.f32(); val shininess = r.f32()
            r.skip(3 * 4)
            val toon = r.u8()
            r.skip(1 + 4)
            val texture = r.fixedString(20, SHIFT_JIS)
            val parts = texture.split("*")
            out.add(MmdMaterialMeta("material_${out.size}", "toon${toon.toString().padStart(2, '0')}.bmp", parts.getOrElse(1) { "" }, "", floatArrayOf(0f, 0f, 0f, alpha), shininess, true))
        }
        return out
    }
    private fun readBones(): List<MmdBoneMeta> {
        val out = ArrayList<MmdBoneMeta>()
        repeat(r.u16()) {
            val name = r.fixedString(20, SHIFT_JIS); val parent = signedIndex(r.u16()); val tail = signedIndex(r.u16()); val type = r.u8(); val ikParent = signedIndex(r.u16()); val pos = Vec3(r.f32(), r.f32(), r.f32())
            out.add(MmdBoneMeta(name, parentIndex = parent, flags = type, position = pos, targetIndex = if (tail >= 0) tail else ikParent))
        }
        return out
    }
    private fun readIkTargets(bones: List<MmdBoneMeta>): Map<Int, MmdIkMeta> {
        val out = HashMap<Int, MmdIkMeta>()
        repeat(r.u16()) {
            val target = signedIndex(r.u16()); val effector = signedIndex(r.u16()); val count = r.u8(); val loops = r.u16(); val limit = r.f32()
            val links = List(count) { MmdIkLinkMeta(signedIndex(r.u16()), false) }
            if (target in bones.indices) out[target] = MmdIkMeta(effector, loops, limit, links)
        }
        return out
    }
    private fun readMorphs(): List<MmdMorphMeta> {
        val out = ArrayList<MmdMorphMeta>()
        repeat(r.u16()) { val name = r.fixedString(20, SHIFT_JIS); val count = r.i32().coerceAtLeast(0); val type = r.u8(); r.skip(count * 16); out.add(MmdMorphMeta(name, type = type, offsetCount = count)) }
        return out
    }
    private fun skipFramesAndToon() {
        r.skip(r.u8())
        repeat(r.u8()) { r.skip(1) }
        repeat(10) { r.fixedString(100, SHIFT_JIS) }
    }
    private fun readEnglishNames(boneCount: Int, morphCount: Int): Pair<String, String> {
        if (r.remaining <= 0 || r.u8() == 0) return "" to ""
        val name = r.fixedString(20, StandardCharsets.US_ASCII); val comment = r.fixedString(256, StandardCharsets.US_ASCII)
        repeat(boneCount) { r.fixedString(20, StandardCharsets.US_ASCII) }
        repeat((morphCount - 1).coerceAtLeast(0)) { r.fixedString(20, StandardCharsets.US_ASCII) }
        repeat(r.u8()) { r.fixedString(50, StandardCharsets.US_ASCII) }
        return name to comment
    }
    private fun readRigidBodies(): List<MmdRigidBodyMeta> {
        if (r.remaining < 4) return emptyList()
        val out = ArrayList<MmdRigidBodyMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.fixedString(20, SHIFT_JIS); val bone = signedIndex(r.u16()); val group = r.u8(); val mask = r.u16(); val shape = r.u8()
            val size = Vec3(r.f32(), r.f32(), r.f32()); val pos = Vec3(r.f32(), r.f32(), r.f32()); val rot = Vec3(r.f32(), r.f32(), r.f32()); val mass = r.f32()
            r.skip(4 * 4)
            out.add(MmdRigidBodyMeta(name, boneIndex = bone, group = group, mask = mask, shape = shape, size = size, position = pos, rotation = rot, mass = mass, mode = r.u8()))
        }
        return out
    }
    private fun readJoints(): List<MmdJointMeta> {
        if (r.remaining < 4) return emptyList()
        val out = ArrayList<MmdJointMeta>()
        repeat(r.i32().coerceAtLeast(0)) {
            val name = r.fixedString(20, SHIFT_JIS); val a = r.i32(); val b = r.i32(); val pos = Vec3(r.f32(), r.f32(), r.f32()); val rot = Vec3(r.f32(), r.f32(), r.f32())
            r.skip(3 * 4 + 3 * 4 + 3 * 4 + 3 * 4 + 3 * 4 + 3 * 4)
            out.add(MmdJointMeta(name, rigidBodyA = a, rigidBodyB = b, position = pos, rotation = rot))
        }
        return out
    }
    private fun signedIndex(v: Int) = if (v == 0xffff) -1 else v
}
private fun complete(model: MmdModelMeta): MmdModelMeta {
    val chains = MmdProfiles.buildChains(model)
    val toon = MmdProfiles.buildToonProfile(model.materials)
    val stage = MmdProfiles.buildStageProfile(model)
    return model.copy(physicsChains = chains, toonProfile = toon, stageProfile = stage)
}
