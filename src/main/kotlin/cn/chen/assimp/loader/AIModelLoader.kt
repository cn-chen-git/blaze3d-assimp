package cn.chen.assimp.loader
import cn.chen.assimp.core.AIAnimChannel as OurChannel
import cn.chen.assimp.core.AIBonePose as OurBonePose
import cn.chen.assimp.core.AIBoneInfo
import cn.chen.assimp.core.AIMeshData
import cn.chen.assimp.core.AINodeGraph
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.core.AIAnimClip
import cn.chen.assimp.core.AIVertex
import cn.chen.assimp.core.AIMorphTarget
import cn.chen.assimp.math.AIMat4
import cn.chen.assimp.math.AIVec3
import cn.chen.assimp.math.AIVec4
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.material.AIPbrMat
import cn.chen.assimp.material.AIAlphaMode
import cn.chen.assimp.material.AITexInfo
import cn.chen.assimp.material.AITexType
import cn.chen.assimp.material.AIEmbeddedTex
import cn.chen.assimp.material.AIKhrClearcoat
import cn.chen.assimp.material.AIKhrExt
import cn.chen.assimp.material.AIKhrSheen
import cn.chen.assimp.material.AIKhrTransmission
import cn.chen.assimp.material.AIKhrUnlit
import cn.chen.assimp.material.AIKhrVolume
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AIAnimMesh
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.AITexture
import org.lwjgl.assimp.Assimp.AI_MATKEY_BASE_COLOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_EMISSIVE
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHACUTOFF
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHAMODE
import org.lwjgl.assimp.Assimp.AI_MATKEY_METALLIC_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_NAME
import org.lwjgl.assimp.Assimp.AI_MATKEY_REFRACTI
import org.lwjgl.assimp.Assimp.AI_MATKEY_ROUGHNESS_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_TWOSIDED
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialFloatArray
import org.lwjgl.assimp.Assimp.aiGetMaterialIntegerArray
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiGetMaterialTexture
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiImportFileFromMemory
import org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace
import org.lwjgl.assimp.Assimp.aiProcess_FlipUVs
import org.lwjgl.assimp.Assimp.aiProcess_GenBoundingBoxes
import org.lwjgl.assimp.Assimp.aiProcess_GenNormals
import org.lwjgl.assimp.Assimp.aiProcess_GlobalScale
import org.lwjgl.assimp.Assimp.aiProcess_ImproveCacheLocality
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiProcess_SortByPType
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiProcess_ValidateDataStructure
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.Assimp.aiReturn_SUCCESS
import org.lwjgl.assimp.Assimp.aiTextureType_BASE_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_CLEARCOAT
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_EMISSION_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_EMISSIVE
import org.lwjgl.assimp.Assimp.aiTextureType_HEIGHT
import org.lwjgl.assimp.Assimp.aiTextureType_LIGHTMAP
import org.lwjgl.assimp.Assimp.aiTextureType_METALNESS
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_OPACITY
import org.lwjgl.assimp.Assimp.aiTextureType_SHEEN
import org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR
import org.lwjgl.assimp.Assimp.aiTextureType_TRANSMISSION
import org.lwjgl.assimp.Assimp.aiTextureType_UNKNOWN
import java.nio.ByteBuffer
object AIModelLoader {
    private const val DEFAULT_FLAGS = aiProcess_Triangulate or aiProcess_GenNormals or
        aiProcess_CalcTangentSpace or aiProcess_FlipUVs or aiProcess_LimitBoneWeights or
        aiProcess_GenBoundingBoxes or aiProcess_JoinIdenticalVertices or
        aiProcess_ValidateDataStructure or aiProcess_ImproveCacheLocality or
        aiProcess_SortByPType or aiProcess_GlobalScale
    fun loadFromFile(path: String, flags: Int = DEFAULT_FLAGS): AISceneData {
        val scene = aiImportFile(path, flags) ?: throw RuntimeException("assimp: ${aiGetErrorString()}")
        return parseScene(scene)
    }
    fun loadFromMemory(buffer: ByteBuffer, hint: String, flags: Int = DEFAULT_FLAGS): AISceneData {
        val scene = aiImportFileFromMemory(buffer, flags, hint) ?: throw RuntimeException("assimp: ${aiGetErrorString()}")
        return parseScene(scene)
    }
    private fun parseScene(scene: AIScene): AISceneData {
        val boneMap = mutableMapOf<String, AIBoneInfo>()
        val meshes = parseMeshes(scene, boneMap)
        val materials = parseMaterials(scene)
        val animations = parseAnimations(scene)
        val rootNode = parseNode(scene.mRootNode()!!, null)
        val embeddedTextures = parseEmbeddedTextures(scene)
        val skeleton = if (boneMap.isNotEmpty()) OurBonePose(boneMap, convertMatrix(scene.mRootNode()!!.mTransformation()).inverse()) else null
        aiReleaseImport(scene)
        return AISceneData(meshes, materials, animations, rootNode, skeleton, embeddedTextures)
    }
    private fun parseMeshes(scene: AIScene, boneMap: MutableMap<String, AIBoneInfo>): List<AIMeshData> {
        val numMeshes = scene.mNumMeshes()
        val meshPtrs = scene.mMeshes()!!
        return (0 until numMeshes).map { i ->
            val mesh = AIMesh.create(meshPtrs.get(i))
            parseMesh(mesh, boneMap)
        }
    }
    private fun parseMesh(mesh: AIMesh, boneMap: MutableMap<String, AIBoneInfo>): AIMeshData {
        val numVerts = mesh.mNumVertices()
        val positions = mesh.mVertices()
        val normals = mesh.mNormals()
        val tangents = mesh.mTangents()
        val bitangents = mesh.mBitangents()
        val texCoords0 = mesh.mTextureCoords(0)
        val texCoords1 = mesh.mTextureCoords(1)
        val colors0 = mesh.mColors(0)
        val boneWeightsMap = HashMap<Int, MutableList<Pair<Int, Float>>>()
        val numBones = mesh.mNumBones()
        if (numBones > 0) {
            val bonePtrs = mesh.mBones()!!
            for (b in 0 until numBones) {
                val bone = AIBone.create(bonePtrs.get(b))
                val boneName = bone.mName().dataString()
                val boneIdx = boneMap.size
                if (!boneMap.containsKey(boneName)) {
                    boneMap[boneName] = AIBoneInfo(boneName, convertMatrix(bone.mOffsetMatrix()), boneIdx)
                }
                val idx = boneMap[boneName]!!.index
                val weights = bone.mWeights()
                for (w in 0 until bone.mNumWeights()) {
                    val vw = weights.get(w)
                    boneWeightsMap.getOrPut(vw.mVertexId()) { mutableListOf() }.add(idx to vw.mWeight())
                }
            }
        }
        val vertices = (0 until numVerts).map { v ->
            val pos = positions.get(v)
            val norm = normals?.get(v)
            val tan = tangents?.get(v)
            val bitan = bitangents?.get(v)
            val uv0 = texCoords0?.get(v)
            val uv1 = texCoords1?.get(v)
            val col = colors0?.get(v)
            val bw = boneWeightsMap[v]
            val boneIds = IntArray(4)
            val boneWts = FloatArray(4)
            if (bw != null && bw.isNotEmpty()) {
                bw.sortedByDescending { it.second }.take(4).forEachIndexed { i, (id, wt) -> boneIds[i] = id; boneWts[i] = wt }
                val sum = boneWts[0] + boneWts[1] + boneWts[2] + boneWts[3]
                if (sum > 0f) { val inv = 1f / sum; boneWts[0] *= inv; boneWts[1] *= inv; boneWts[2] *= inv; boneWts[3] *= inv }
            }
            AIVertex(
                position = AIVec3(pos.x(), pos.y(), pos.z()),
                normal = if (norm != null) AIVec3(norm.x(), norm.y(), norm.z()) else AIVec3(0f, 1f, 0f),
                tangent = if (tan != null) AIVec3(tan.x(), tan.y(), tan.z()) else AIVec3(1f, 0f, 0f),
                bitangent = if (bitan != null) AIVec3(bitan.x(), bitan.y(), bitan.z()) else AIVec3(0f, 0f, 1f),
                texCoord0 = if (uv0 != null) AIVec3(uv0.x(), uv0.y(), uv0.z()) else AIVec3(),
                texCoord1 = if (uv1 != null) AIVec3(uv1.x(), uv1.y(), uv1.z()) else AIVec3(),
                color = if (col != null) AIVec4(col.r(), col.g(), col.b(), col.a()) else AIVec4(1f, 1f, 1f, 1f),
                boneIds = boneIds,
                boneWeights = boneWts
            )
        }
        val indices = mutableListOf<Int>()
        for (f in 0 until mesh.mNumFaces()) {
            val face = mesh.mFaces().get(f)
            val faceIndices = face.mIndices()
            for (j in 0 until face.mNumIndices()) indices.add(faceIndices.get(j))
        }
        val morphTargets = parseMorphTargets(mesh, numVerts)
        return AIMeshData(
            name = mesh.mName().dataString(),
            vertices = vertices,
            indices = indices.toIntArray(),
            materialIndex = mesh.mMaterialIndex(),
            bones = boneMap.values.toList(),
            morphTargets = morphTargets
        )
    }
    private fun parseMaterials(scene: AIScene): List<AIPbrMat> {
        val numMats = scene.mNumMaterials()
        if (numMats == 0) return listOf(AIPbrMat())
        val matPtrs = scene.mMaterials()!!
        return (0 until numMats).map { i ->
            val mat = AIMaterial.create(matPtrs.get(i))
            parseMaterial(mat)
        }
    }
    private fun parseMaterial(mat: AIMaterial): AIPbrMat {
        val textures = mutableMapOf<AITexType, AITexInfo>()
        extractTexture(mat, aiTextureType_DIFFUSE, AITexType.ALBEDO)?.let { textures[AITexType.ALBEDO] = it }
        extractTexture(mat, aiTextureType_BASE_COLOR, AITexType.ALBEDO)?.let { textures.putIfAbsent(AITexType.ALBEDO, it) }
        extractTexture(mat, aiTextureType_NORMALS, AITexType.NORMAL)?.let { textures[AITexType.NORMAL] = it }
        extractTexture(mat, aiTextureType_UNKNOWN, AITexType.METALLIC_ROUGHNESS)?.let { textures[AITexType.METALLIC_ROUGHNESS] = it }
        extractTexture(mat, aiTextureType_METALNESS, AITexType.METALLIC_ROUGHNESS)?.let { textures.putIfAbsent(AITexType.METALLIC_ROUGHNESS, it) }
        extractTexture(mat, aiTextureType_LIGHTMAP, AITexType.OCCLUSION)?.let { textures[AITexType.OCCLUSION] = it }
        extractTexture(mat, aiTextureType_EMISSIVE, AITexType.EMISSIVE)?.let { textures[AITexType.EMISSIVE] = it }
        extractTexture(mat, aiTextureType_EMISSION_COLOR, AITexType.EMISSIVE)?.let { textures.putIfAbsent(AITexType.EMISSIVE, it) }
        extractTexture(mat, aiTextureType_SPECULAR, AITexType.SPECULAR)?.let { textures[AITexType.SPECULAR] = it }
        extractTexture(mat, aiTextureType_HEIGHT, AITexType.HEIGHT)?.let { textures[AITexType.HEIGHT] = it }
        extractTexture(mat, aiTextureType_OPACITY, AITexType.OPACITY)?.let { textures[AITexType.OPACITY] = it }
        extractTexture(mat, aiTextureType_CLEARCOAT, AITexType.CLEARCOAT)?.let { textures[AITexType.CLEARCOAT] = it }
        extractTexture(mat, aiTextureType_SHEEN, AITexType.SHEEN_COLOR)?.let { textures[AITexType.SHEEN_COLOR] = it }
        extractTexture(mat, aiTextureType_TRANSMISSION, AITexType.TRANSMISSION)?.let { textures[AITexType.TRANSMISSION] = it }
        val color = AIColor4D.create()
        var baseColor = AIVec4(1f, 1f, 1f, 1f)
        if (aiGetMaterialColor(mat, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            baseColor = AIVec4(color.r(), color.g(), color.b(), color.a())
        } else if (aiGetMaterialColor(mat, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            baseColor = AIVec4(color.r(), color.g(), color.b(), color.a())
        }
        val f1 = floatArrayOf(0f); val max = intArrayOf(1)
        var metallic = 0f; var roughness = 1f; var ior = 1.5f
        var normalScale = 1f; var occlusionStrength = 1f; var emissiveStrength = 1f; var alphaCutoff = 0.5f
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) metallic = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) roughness = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_REFRACTI, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) ior = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_GLTF_ALPHACUTOFF, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) alphaCutoff = f1[0]
        val emissive = floatArrayOf(0f, 0f, 0f)
        val emColor = AIColor4D.create()
        if (aiGetMaterialColor(mat, AI_MATKEY_COLOR_EMISSIVE, aiTextureType_NONE, 0, emColor) == aiReturn_SUCCESS) {
            emissive[0] = emColor.r(); emissive[1] = emColor.g(); emissive[2] = emColor.b()
        }
        var doubleSided = false
        val intOut = intArrayOf(0); val imax = intArrayOf(1)
        if (aiGetMaterialIntegerArray(mat, AI_MATKEY_TWOSIDED, aiTextureType_NONE, 0, intOut, imax) == aiReturn_SUCCESS) doubleSided = intOut[0] != 0
        var alphaMode = AIAlphaMode.OPAQUE
        val alphaStr = AIString.create()
        if (aiGetMaterialString(mat, AI_MATKEY_GLTF_ALPHAMODE, aiTextureType_NONE, 0, alphaStr) == aiReturn_SUCCESS) {
            alphaMode = when (alphaStr.dataString().uppercase()) {
                "MASK" -> AIAlphaMode.MASK
                "BLEND" -> AIAlphaMode.BLEND
                else -> AIAlphaMode.OPAQUE
            }
        }
        val khr = parseKhrExtensions(mat)
        val name = AIString.create()
        aiGetMaterialString(mat, AI_MATKEY_NAME, aiTextureType_NONE, 0, name)
        return AIPbrMat(
            name = name.dataString(),
            baseColorFactor = baseColor,
            metallicFactor = metallic,
            roughnessFactor = roughness,
            emissiveFactor = emissive,
            emissiveStrength = emissiveStrength,
            normalScale = normalScale,
            occlusionStrength = occlusionStrength,
            alphaMode = alphaMode,
            alphaCutoff = alphaCutoff,
            doubleSided = doubleSided,
            ior = ior,
            textures = textures,
            khrExtensions = khr
        )
    }
    private fun parseKhrExtensions(mat: AIMaterial): AIKhrExt {
        val f1 = floatArrayOf(0f); val max = intArrayOf(1)
        var clearcoatFactor = 0f; var clearcoatRoughness = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, "\$mat.gltf.clearcoatFactor", 0, 0, f1, max) == aiReturn_SUCCESS) clearcoatFactor = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, "\$mat.gltf.clearcoatRoughnessFactor", 0, 0, f1, max) == aiReturn_SUCCESS) clearcoatRoughness = f1[0]
        val clearcoat = if (clearcoatFactor > 0f) AIKhrClearcoat(clearcoatFactor, clearcoatRoughness) else null
        var sheenRoughness = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, "\$mat.gltf.sheenRoughnessFactor", 0, 0, f1, max) == aiReturn_SUCCESS) sheenRoughness = f1[0]
        val sheenCol = AIColor4D.create()
        var sheenColor = AIVec3()
        if (aiGetMaterialColor(mat, "\$mat.gltf.sheenColorFactor", 0, 0, sheenCol) == aiReturn_SUCCESS) {
            sheenColor = AIVec3(sheenCol.r(), sheenCol.g(), sheenCol.b())
        }
        val sheen = if (sheenRoughness > 0f || sheenColor.length() > 0f) AIKhrSheen(sheenColor, sheenRoughness) else null
        var transmissionFactor = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, "\$mat.gltf.transmissionFactor", 0, 0, f1, max) == aiReturn_SUCCESS) transmissionFactor = f1[0]
        val transmission = if (transmissionFactor > 0f) AIKhrTransmission(transmissionFactor) else null
        var thicknessFactor = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, "\$mat.gltf.thicknessFactor", 0, 0, f1, max) == aiReturn_SUCCESS) thicknessFactor = f1[0]
        val volume = if (thicknessFactor > 0f) AIKhrVolume(thicknessFactor) else null
        var unlit = false
        val i1 = intArrayOf(0); val imax = intArrayOf(1)
        if (aiGetMaterialIntegerArray(mat, "\$mat.gltf.unlit", 0, 0, i1, imax) == aiReturn_SUCCESS) unlit = i1[0] != 0
        val unlitExt = if (unlit) AIKhrUnlit(true) else null
        return AIKhrExt(
            clearcoat = clearcoat,
            sheen = sheen,
            transmission = transmission,
            volume = volume,
            unlit = unlitExt
        )
    }
    private fun extractTexture(mat: AIMaterial, type: Int, texType: AITexType): AITexInfo? {
        val path = AIString.create()
        if (aiGetMaterialTexture(mat, type, 0, path, null as IntArray?, null, null, null, null, null) == aiReturn_SUCCESS) {
            val p = path.dataString()
            if (p.isNotEmpty()) return AITexInfo(path = p, type = texType)
        }
        return null
    }
    private fun parseMorphTargets(mesh: AIMesh, numVerts: Int): List<AIMorphTarget> {
        val numAnimMeshes = mesh.mNumAnimMeshes()
        if (numAnimMeshes == 0) return emptyList()
        val ptrs = mesh.mAnimMeshes()!!
        return (0 until numAnimMeshes).map { i ->
            val am = AIAnimMesh.create(ptrs.get(i))
            val pos = am.mVertices()
            val norm = am.mNormals()
            val tan = am.mTangents()
            val positions = if (pos != null) (0 until numVerts).map { v -> val p = pos.get(v); AIVec3(p.x(), p.y(), p.z()) } else emptyList()
            val normals = if (norm != null) (0 until numVerts).map { v -> val n = norm.get(v); AIVec3(n.x(), n.y(), n.z()) } else emptyList()
            val tangents = if (tan != null) (0 until numVerts).map { v -> val t = tan.get(v); AIVec3(t.x(), t.y(), t.z()) } else emptyList()
            AIMorphTarget(am.mName().dataString(), positions, normals, tangents)
        }
    }
    private fun parseAnimations(scene: AIScene): List<AIAnimClip> {
        val numAnims = scene.mNumAnimations()
        if (numAnims == 0) return emptyList()
        val animPtrs = scene.mAnimations()!!
        return (0 until numAnims).map { i ->
            val anim = AIAnimation.create(animPtrs.get(i))
            parseAnimation(anim)
        }
    }
    private fun parseAnimation(anim: AIAnimation): AIAnimClip {
        val numChannels = anim.mNumChannels()
        val channelPtrs = anim.mChannels()!!
        val channels = (0 until numChannels).map { i ->
            val channel = AINodeAnim.create(channelPtrs.get(i))
            val np = channel.mNumPositionKeys(); val nr = channel.mNumRotationKeys(); val ns = channel.mNumScalingKeys()
            val posTimes = DoubleArray(np); val posVals = FloatArray(np * 3)
            val rotTimes = DoubleArray(nr); val rotVals = FloatArray(nr * 4)
            val sclTimes = DoubleArray(ns); val sclVals = FloatArray(ns * 3)
            val pk = channel.mPositionKeys()!!; val rk = channel.mRotationKeys()!!; val sk = channel.mScalingKeys()!!
            for (k in 0 until np) { val key = pk.get(k); posTimes[k] = key.mTime(); val v = key.mValue(); val o = k*3; posVals[o] = v.x(); posVals[o+1] = v.y(); posVals[o+2] = v.z() }
            for (k in 0 until nr) { val key = rk.get(k); rotTimes[k] = key.mTime(); val v = key.mValue(); val o = k*4; rotVals[o] = v.x(); rotVals[o+1] = v.y(); rotVals[o+2] = v.z(); rotVals[o+3] = v.w() }
            for (k in 0 until ns) { val key = sk.get(k); sclTimes[k] = key.mTime(); val v = key.mValue(); val o = k*3; sclVals[o] = v.x(); sclVals[o+1] = v.y(); sclVals[o+2] = v.z() }
            OurChannel(channel.mNodeName().dataString(), posTimes, posVals, rotTimes, rotVals, sclTimes, sclVals)
        }
        return AIAnimClip(anim.mName().dataString(), anim.mDuration(), anim.mTicksPerSecond(), channels)
    }
    private fun parseNode(node: AINode, parent: AINodeGraph?): AINodeGraph {
        val meshCount = node.mNumMeshes()
        val meshIndices = if (meshCount > 0) {
            val buf = node.mMeshes()!!
            IntArray(meshCount) { buf.get(it) }
        } else IntArray(0)
        val graph = AINodeGraph(
            name = node.mName().dataString(),
            transform = convertMatrix(node.mTransformation()),
            meshIndices = meshIndices,
            parent = parent
        )
        val numChildren = node.mNumChildren()
        if (numChildren > 0) {
            val childPtrs = node.mChildren()!!
            for (i in 0 until numChildren) {
                val child = AINode.create(childPtrs.get(i))
                graph.children.add(parseNode(child, graph))
            }
        }
        return graph
    }
    private fun parseEmbeddedTextures(scene: AIScene): List<AIEmbeddedTex> {
        val numTex = scene.mNumTextures()
        if (numTex == 0) return emptyList()
        val texPtrs = scene.mTextures()!!
        return (0 until numTex).map { i ->
            val tex = AITexture.create(texPtrs.get(i))
            val w = tex.mWidth(); val h = tex.mHeight()
            val data = ByteArray(if (h == 0) w else w * h * 4)
            if (h == 0) {
                tex.pcDataCompressed().get(data)
            } else {
                val texels = tex.pcData()
                for (j in 0 until w * h) {
                    val t = texels.get(j)
                    data[j * 4] = t.r(); data[j * 4 + 1] = t.g(); data[j * 4 + 2] = t.b(); data[j * 4 + 3] = t.a()
                }
            }
            AIEmbeddedTex(w, h, data, tex.achFormatHintString())
        }
    }
    private fun convertMatrix(m: AIMatrix4x4): AIMat4 {
        return AIMat4(floatArrayOf(
            m.a1(), m.a2(), m.a3(), m.a4(),
            m.b1(), m.b2(), m.b3(), m.b4(),
            m.c1(), m.c2(), m.c3(), m.c4(),
            m.d1(), m.d2(), m.d3(), m.d4()
        ))
    }
}
