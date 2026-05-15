package cn.chen.blaze3d.loader
import cn.chen.blaze3d.core.AnimChannel as OurChannel
import cn.chen.blaze3d.core.BonePose as OurBonePose
import cn.chen.blaze3d.core.BoneInfo
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.AnimBehaviour
import cn.chen.blaze3d.core.MeshAnimChannel
import cn.chen.blaze3d.core.MorphAnimChannel
import cn.chen.blaze3d.core.MorphKey
import cn.chen.blaze3d.core.Vertex
import cn.chen.blaze3d.core.MorphTarget
import cn.chen.blaze3d.api.ModelFeature
import cn.chen.blaze3d.api.ModelFormatProvider
import cn.chen.blaze3d.api.ModelFormats
import cn.chen.blaze3d.api.ModelLoadContext
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Vec3
import cn.chen.blaze3d.math.Vec4
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.material.Material as OurMaterial
import cn.chen.blaze3d.material.AlphaMode
import cn.chen.blaze3d.material.TexInfo
import cn.chen.blaze3d.material.TexType
import cn.chen.blaze3d.material.EmbeddedTex
import cn.chen.blaze3d.material.KhrClearcoat
import cn.chen.blaze3d.material.KhrAnisotropy
import cn.chen.blaze3d.material.KhrEmissiveStrength
import cn.chen.blaze3d.material.KhrExt
import cn.chen.blaze3d.material.KhrSheen
import cn.chen.blaze3d.material.KhrSpecular
import cn.chen.blaze3d.material.KhrTransmission
import cn.chen.blaze3d.material.KhrUnlit
import cn.chen.blaze3d.material.KhrVolume
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AIAnimMesh
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIMeshAnim
import org.lwjgl.assimp.AIMeshMorphAnim
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.AITexture
import org.lwjgl.assimp.AIUVTransform
import org.lwjgl.assimp.AIVector3D
import org.lwjgl.assimp.Assimp.AI_MATKEY_ANISOTROPY_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_BASE_COLOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_BUMPSCALING
import org.lwjgl.assimp.Assimp.AI_MATKEY_CLEARCOAT_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_CLEARCOAT_ROUGHNESS_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_EMISSIVE
import org.lwjgl.assimp.Assimp.AI_MATKEY_EMISSIVE_INTENSITY
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLOSSINESS_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHACUTOFF
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHAMODE
import org.lwjgl.assimp.Assimp.AI_MATKEY_METALLIC_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_NAME
import org.lwjgl.assimp.Assimp.AI_MATKEY_OPACITY
import org.lwjgl.assimp.Assimp.AI_MATKEY_REFRACTI
import org.lwjgl.assimp.Assimp.AI_MATKEY_ROUGHNESS_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_SHEEN_COLOR_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_SHEEN_ROUGHNESS_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_SHININESS
import org.lwjgl.assimp.Assimp.AI_MATKEY_SPECULAR_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_TRANSMISSION_FACTOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_TWOSIDED
import org.lwjgl.assimp.Assimp.AI_MATKEY_VOLUME_ATTENUATION_COLOR
import org.lwjgl.assimp.Assimp.AI_MATKEY_VOLUME_ATTENUATION_DISTANCE
import org.lwjgl.assimp.Assimp.AI_MATKEY_VOLUME_THICKNESS_FACTOR
import org.lwjgl.assimp.Assimp._AI_MATKEY_UVTRANSFORM_BASE
import org.lwjgl.assimp.Assimp.aiAnimBehaviour_CONSTANT
import org.lwjgl.assimp.Assimp.aiAnimBehaviour_LINEAR
import org.lwjgl.assimp.Assimp.aiAnimBehaviour_REPEAT
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiIsExtensionSupported
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialFloatArray
import org.lwjgl.assimp.Assimp.aiGetMaterialIntegerArray
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiGetMaterialTexture
import org.lwjgl.assimp.Assimp.aiGetMaterialUVTransform
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiImportFileFromMemory
import org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace
import org.lwjgl.assimp.Assimp.aiProcess_FlipUVs
import org.lwjgl.assimp.Assimp.aiProcess_GenBoundingBoxes
import org.lwjgl.assimp.Assimp.aiProcess_GenNormals
import org.lwjgl.assimp.Assimp.aiProcess_GenUVCoords
import org.lwjgl.assimp.Assimp.aiProcess_GlobalScale
import org.lwjgl.assimp.Assimp.aiProcess_ImproveCacheLocality
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiProcess_OptimizeGraph
import org.lwjgl.assimp.Assimp.aiProcess_OptimizeMeshes
import org.lwjgl.assimp.Assimp.aiProcess_SortByPType
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiProcess_ValidateDataStructure
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.Assimp.aiReturn_SUCCESS
import org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT_OCCLUSION
import org.lwjgl.assimp.Assimp.aiTextureType_ANISOTROPY
import org.lwjgl.assimp.Assimp.aiTextureType_BASE_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_CLEARCOAT
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE_ROUGHNESS
import org.lwjgl.assimp.Assimp.aiTextureType_DISPLACEMENT
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_EMISSION_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_EMISSIVE
import org.lwjgl.assimp.Assimp.aiTextureType_GLTF_METALLIC_ROUGHNESS
import org.lwjgl.assimp.Assimp.aiTextureType_HEIGHT
import org.lwjgl.assimp.Assimp.aiTextureType_LIGHTMAP
import org.lwjgl.assimp.Assimp.aiTextureType_MAYA_BASE
import org.lwjgl.assimp.Assimp.aiTextureType_MAYA_SPECULAR
import org.lwjgl.assimp.Assimp.aiTextureType_MAYA_SPECULAR_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_MAYA_SPECULAR_ROUGHNESS
import org.lwjgl.assimp.Assimp.aiTextureType_METALNESS
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.assimp.Assimp.aiTextureType_NORMAL_CAMERA
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_OPACITY
import org.lwjgl.assimp.Assimp.aiTextureType_SHEEN
import org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR
import org.lwjgl.assimp.Assimp.aiTextureType_TRANSMISSION
import org.lwjgl.assimp.Assimp.aiTextureType_UNKNOWN
import java.nio.ByteBuffer
object UniversalModelLoader : ModelFormatProvider {
    override val support = ModelSupport("universal", "Universal Model", ModelFormats.ASSIMP_EXTENSIONS - setOf("gltf", "glb", "fbx", "obj", "dae", "pmd", "pmx"), setOf(ModelFeature.MESH, ModelFeature.MATERIALS, ModelFeature.TEXTURES, ModelFeature.ANIMATION, ModelFeature.SKINNING, ModelFeature.MORPHS, ModelFeature.NODES, ModelFeature.CAMERAS, ModelFeature.LIGHTS), ModelFormats.RESOURCE_EXTENSIONS)
    private const val DEFAULT_FLAGS = aiProcess_Triangulate or aiProcess_GenNormals or
        aiProcess_CalcTangentSpace or aiProcess_FlipUVs or aiProcess_LimitBoneWeights or
        aiProcess_GenBoundingBoxes or aiProcess_JoinIdenticalVertices or
        aiProcess_ValidateDataStructure or aiProcess_ImproveCacheLocality or
        aiProcess_SortByPType or aiProcess_GlobalScale or aiProcess_GenUVCoords or
        aiProcess_OptimizeMeshes or aiProcess_OptimizeGraph
    override fun load(context: ModelLoadContext) = loadFromFile(context.path, if (context.flags == 0) DEFAULT_FLAGS else context.flags)
    fun loadFromFile(path: String, flags: Int = DEFAULT_FLAGS): SceneData {
        val key = AssetFingerprint.of(path, flags)
        ModelCache.get(key)?.let { return it }
        val ext = ModelFormats.extension(path)
        if (ext.isNotEmpty() && !aiIsExtensionSupported(ext)) throw IllegalArgumentException("model: unsupported extension $ext")
        val scene = aiImportFile(path, flags) ?: throw RuntimeException("model: ${aiGetErrorString()}")
        return ModelCache.put(key, parseScene(scene))
    }
    fun loadFromMemory(buffer: ByteBuffer, hint: String, flags: Int = DEFAULT_FLAGS): SceneData {
        val scene = aiImportFileFromMemory(buffer, flags, hint) ?: throw RuntimeException("model: ${aiGetErrorString()}")
        return parseScene(scene)
    }
    private fun parseScene(scene: AIScene): SceneData {
        val boneMap = mutableMapOf<String, BoneInfo>()
        val meshes = parseMeshes(scene, boneMap)
        val materials = parseMaterials(scene)
        val animations = parseAnimations(scene)
        val sourceRoot = scene.mRootNode() ?: throw IllegalArgumentException("model: missing root node")
        val rootNode = parseNode(sourceRoot, null)
        val embeddedTextures = parseEmbeddedTextures(scene)
        val skeleton = if (boneMap.isNotEmpty()) OurBonePose(boneMap, convertMatrix(sourceRoot.mTransformation()).inverse()) else null
        aiReleaseImport(scene)
        return SceneData(meshes, materials, animations, rootNode, skeleton, embeddedTextures)
    }
    private fun parseMeshes(scene: AIScene, boneMap: MutableMap<String, BoneInfo>): List<MeshData> {
        val numMeshes = scene.mNumMeshes()
        if (numMeshes == 0) return emptyList()
        val meshPtrs = scene.mMeshes() ?: return emptyList()
        return List(numMeshes) { i -> parseMesh(AIMesh.create(meshPtrs.get(i)), boneMap) }
    }
    private fun parseMesh(mesh: AIMesh, boneMap: MutableMap<String, BoneInfo>): MeshData {
        val numVerts = mesh.mNumVertices()
        val positions = mesh.mVertices()
        val normals = mesh.mNormals()
        val tangents = mesh.mTangents()
        val bitangents = mesh.mBitangents()
        val texCoords0 = mesh.mTextureCoords(0)
        val texCoords1 = mesh.mTextureCoords(1)
        val colors0 = mesh.mColors(0)
        val faces = mesh.mFaces(); val numFaces = mesh.mNumFaces()
        var indexCount = 0
        for (f in 0 until numFaces) indexCount += faces.get(f).mNumIndices()
        val indices = IntArray(indexCount); var cursor = 0
        for (f in 0 until numFaces) { val face = faces.get(f); val fi = face.mIndices(); val nf = face.mNumIndices(); for (j in 0 until nf) indices[cursor++] = fi.get(j) }
        val generatedNormals = if (normals == null) generateNormals(positions, numVerts, indices) else null
        val boneIdsByVertex = Array(numVerts) { IntArray(4) { -1 } }
        val boneWeightsByVertex = Array(numVerts) { FloatArray(4) }
        val numBones = mesh.mNumBones()
        if (numBones > 0) {
            mesh.mBones()?.let { bonePtrs ->
                for (b in 0 until numBones) {
                    val bone = AIBone.create(bonePtrs.get(b))
                    val boneName = bone.mName().dataString()
                    val idx = boneMap.getOrPut(boneName) { BoneInfo(boneName, convertMatrix(bone.mOffsetMatrix()), boneMap.size) }.index
                    val weights = bone.mWeights()
                    for (w in 0 until bone.mNumWeights()) {
                        val vw = weights.get(w)
                        val vertexId = vw.mVertexId()
                        if (vertexId in 0 until numVerts) insertBoneWeight(boneIdsByVertex[vertexId], boneWeightsByVertex[vertexId], idx, vw.mWeight())
                    }
                }
            }
        }
        val vertices = ArrayList<Vertex>(numVerts)
        for (v in 0 until numVerts) {
            val pos = positions.get(v)
            val norm = normals?.get(v)
            val genNorm = generatedNormals?.get(v)
            val tan = tangents?.get(v)
            val bitan = bitangents?.get(v)
            val uv0 = texCoords0?.get(v)
            val uv1 = texCoords1?.get(v)
            val col = colors0?.get(v)
            val boneIds = boneIdsByVertex[v]
            val boneWts = boneWeightsByVertex[v]
            if (boneIds[0] >= 0) {
                val sum = boneWts[0] + boneWts[1] + boneWts[2] + boneWts[3]
                if (sum > 0f) { val inv = 1f / sum; boneWts[0] *= inv; boneWts[1] *= inv; boneWts[2] *= inv; boneWts[3] *= inv }
            }
            vertices.add(Vertex(
                position = Vec3(pos.x(), pos.y(), pos.z()),
                normal = if (norm != null) Vec3(norm.x(), norm.y(), norm.z()) else genNorm ?: Vec3(0f, 1f, 0f),
                tangent = if (tan != null) Vec3(tan.x(), tan.y(), tan.z()) else Vec3(1f, 0f, 0f),
                bitangent = if (bitan != null) Vec3(bitan.x(), bitan.y(), bitan.z()) else Vec3(0f, 0f, 1f),
                texCoord0 = if (uv0 != null) Vec3(uv0.x(), uv0.y(), uv0.z()) else Vec3(),
                texCoord1 = if (uv1 != null) Vec3(uv1.x(), uv1.y(), uv1.z()) else Vec3(),
                color = if (col != null) Vec4(col.r(), col.g(), col.b(), col.a()) else Vec4(1f, 1f, 1f, 1f),
                boneIds = boneIds,
                boneWeights = boneWts
            ))
        }
        val morphTargets = parseMorphTargets(mesh, numVerts)
        return MeshData(
            name = mesh.mName().dataString(),
            vertices = vertices,
            indices = indices,
            materialIndex = mesh.mMaterialIndex(),
            bones = boneMap.values.toList(),
            morphTargets = morphTargets
        )
    }
    private fun parseMaterials(scene: AIScene): List<OurMaterial> {
        val numMats = scene.mNumMaterials()
        if (numMats == 0) return listOf(OurMaterial())
        val matPtrs = scene.mMaterials() ?: return listOf(OurMaterial())
        return List(numMats) { i -> parseMaterial(AIMaterial.create(matPtrs.get(i))) }
    }
    private fun parseMaterial(mat: AIMaterial): OurMaterial {
        val textures = mutableMapOf<TexType, TexInfo>()
        extractTexture(mat, aiTextureType_DIFFUSE, TexType.ALBEDO)?.let { textures[TexType.ALBEDO] = it }
        extractTexture(mat, aiTextureType_BASE_COLOR, TexType.ALBEDO)?.let { textures.putIfAbsent(TexType.ALBEDO, it) }
        extractTexture(mat, aiTextureType_MAYA_BASE, TexType.ALBEDO)?.let { textures.putIfAbsent(TexType.ALBEDO, it) }
        extractTexture(mat, aiTextureType_NORMALS, TexType.NORMAL)?.let { textures[TexType.NORMAL] = it }
        extractTexture(mat, aiTextureType_NORMAL_CAMERA, TexType.NORMAL)?.let { textures.putIfAbsent(TexType.NORMAL, it) }
        extractTexture(mat, aiTextureType_HEIGHT, TexType.HEIGHT)?.let { textures[TexType.HEIGHT] = it }
        extractTexture(mat, aiTextureType_DISPLACEMENT, TexType.HEIGHT)?.let { textures.putIfAbsent(TexType.HEIGHT, it) }
        extractTexture(mat, aiTextureType_GLTF_METALLIC_ROUGHNESS, TexType.METALLIC_ROUGHNESS)?.let { textures[TexType.METALLIC_ROUGHNESS] = it }
        extractTexture(mat, aiTextureType_UNKNOWN, TexType.METALLIC_ROUGHNESS)?.let { textures.putIfAbsent(TexType.METALLIC_ROUGHNESS, it) }
        extractTexture(mat, aiTextureType_METALNESS, TexType.METALLIC_ROUGHNESS)?.let { textures.putIfAbsent(TexType.METALLIC_ROUGHNESS, it) }
        extractTexture(mat, aiTextureType_DIFFUSE_ROUGHNESS, TexType.GLOSSINESS)?.let { textures[TexType.GLOSSINESS] = it }
        extractTexture(mat, aiTextureType_MAYA_SPECULAR_ROUGHNESS, TexType.GLOSSINESS)?.let { textures.putIfAbsent(TexType.GLOSSINESS, it) }
        extractTexture(mat, aiTextureType_LIGHTMAP, TexType.OCCLUSION)?.let { textures[TexType.OCCLUSION] = it }
        extractTexture(mat, aiTextureType_AMBIENT_OCCLUSION, TexType.OCCLUSION)?.let { textures.putIfAbsent(TexType.OCCLUSION, it) }
        extractTexture(mat, aiTextureType_EMISSIVE, TexType.EMISSIVE)?.let { textures[TexType.EMISSIVE] = it }
        extractTexture(mat, aiTextureType_EMISSION_COLOR, TexType.EMISSIVE)?.let { textures.putIfAbsent(TexType.EMISSIVE, it) }
        extractTexture(mat, aiTextureType_SPECULAR, TexType.SPECULAR)?.let { textures[TexType.SPECULAR] = it }
        extractTexture(mat, aiTextureType_MAYA_SPECULAR, TexType.SPECULAR)?.let { textures.putIfAbsent(TexType.SPECULAR, it) }
        extractTexture(mat, aiTextureType_MAYA_SPECULAR_COLOR, TexType.SPECULAR_COLOR)?.let { textures[TexType.SPECULAR_COLOR] = it }
        extractTexture(mat, aiTextureType_OPACITY, TexType.OPACITY)?.let { textures[TexType.OPACITY] = it }
        extractTexture(mat, aiTextureType_CLEARCOAT, TexType.CLEARCOAT)?.let { textures[TexType.CLEARCOAT] = it }
        extractTexture(mat, aiTextureType_SHEEN, TexType.SHEEN_COLOR)?.let { textures[TexType.SHEEN_COLOR] = it }
        extractTexture(mat, aiTextureType_TRANSMISSION, TexType.TRANSMISSION)?.let { textures[TexType.TRANSMISSION] = it }
        extractTexture(mat, aiTextureType_ANISOTROPY, TexType.ANISOTROPY)?.let { textures[TexType.ANISOTROPY] = it }
        val color = AIColor4D.create()
        var baseColor = Vec4(1f, 1f, 1f, 1f)
        if (aiGetMaterialColor(mat, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            baseColor = Vec4(color.r(), color.g(), color.b(), color.a())
        } else if (aiGetMaterialColor(mat, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            baseColor = Vec4(color.r(), color.g(), color.b(), color.a())
        }
        val f1 = floatArrayOf(0f); val max = intArrayOf(1)
        var metallic = 0f; var roughness = 1f; var ior = 1.5f
        var opacity = 1f; var shininess = 0f; var specularFactor = 1f; var glossinessFactor = 1f; var anisotropyFactor = 0f
        var normalScale = 1f; var occlusionStrength = 1f; var emissiveStrength = 1f; var alphaCutoff = 0.5f
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) metallic = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) roughness = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_REFRACTI, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) ior = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) opacity = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_SHININESS, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) shininess = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_SPECULAR_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) specularFactor = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_GLOSSINESS_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) glossinessFactor = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_ANISOTROPY_FACTOR, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) anisotropyFactor = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_BUMPSCALING, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) normalScale = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_EMISSIVE_INTENSITY, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) emissiveStrength = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_GLTF_ALPHACUTOFF, aiTextureType_NONE, 0, f1, max) == aiReturn_SUCCESS) alphaCutoff = f1[0]
        if (opacity < 0.999f) baseColor = Vec4(baseColor.x, baseColor.y, baseColor.z, baseColor.w * opacity.coerceIn(0f, 1f))
        val emissive = floatArrayOf(0f, 0f, 0f)
        val emColor = AIColor4D.create()
        if (aiGetMaterialColor(mat, AI_MATKEY_COLOR_EMISSIVE, aiTextureType_NONE, 0, emColor) == aiReturn_SUCCESS) {
            emissive[0] = emColor.r(); emissive[1] = emColor.g(); emissive[2] = emColor.b()
        }
        var doubleSided = false
        val intOut = intArrayOf(0); val imax = intArrayOf(1)
        if (aiGetMaterialIntegerArray(mat, AI_MATKEY_TWOSIDED, aiTextureType_NONE, 0, intOut, imax) == aiReturn_SUCCESS) doubleSided = intOut[0] != 0
        var alphaMode = AlphaMode.OPAQUE
        val alphaStr = AIString.create()
        if (aiGetMaterialString(mat, AI_MATKEY_GLTF_ALPHAMODE, aiTextureType_NONE, 0, alphaStr) == aiReturn_SUCCESS) {
            alphaMode = when (alphaStr.dataString().uppercase()) {
                "MASK" -> AlphaMode.MASK
                "BLEND" -> AlphaMode.BLEND
                else -> AlphaMode.OPAQUE
            }
        }
        val khr = parseKhrExtensions(mat)
        val name = AIString.create()
        aiGetMaterialString(mat, AI_MATKEY_NAME, aiTextureType_NONE, 0, name)
        return OurMaterial(
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
            opacity = opacity,
            shininess = shininess,
            specularFactor = specularFactor,
            glossinessFactor = glossinessFactor,
            anisotropyFactor = anisotropyFactor,
            textures = textures,
            khrExtensions = khr
        )
    }
    private fun parseKhrExtensions(mat: AIMaterial): KhrExt {
        val f1 = floatArrayOf(0f); val max = intArrayOf(1)
        var clearcoatFactor = 0f; var clearcoatRoughness = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_CLEARCOAT_FACTOR, 0, 0, f1, max) == aiReturn_SUCCESS) clearcoatFactor = f1[0]
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_CLEARCOAT_ROUGHNESS_FACTOR, 0, 0, f1, max) == aiReturn_SUCCESS) clearcoatRoughness = f1[0]
        val clearcoatTex = extractTexture(mat, aiTextureType_CLEARCOAT, TexType.CLEARCOAT)
        val clearcoat = if (clearcoatFactor > 0f || clearcoatTex != null) KhrClearcoat(clearcoatFactor, clearcoatRoughness, clearcoatTex) else null
        var sheenRoughness = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_SHEEN_ROUGHNESS_FACTOR, 0, 0, f1, max) == aiReturn_SUCCESS) sheenRoughness = f1[0]
        val sheenCol = AIColor4D.create()
        var sheenColor = Vec3()
        if (aiGetMaterialColor(mat, AI_MATKEY_SHEEN_COLOR_FACTOR, 0, 0, sheenCol) == aiReturn_SUCCESS) {
            sheenColor = Vec3(sheenCol.r(), sheenCol.g(), sheenCol.b())
        }
        val sheenTex = extractTexture(mat, aiTextureType_SHEEN, TexType.SHEEN_COLOR)
        val sheen = if (sheenRoughness > 0f || sheenColor.length() > 0f || sheenTex != null) KhrSheen(sheenColor, sheenRoughness, sheenTex) else null
        var transmissionFactor = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_TRANSMISSION_FACTOR, 0, 0, f1, max) == aiReturn_SUCCESS) transmissionFactor = f1[0]
        val transmissionTex = extractTexture(mat, aiTextureType_TRANSMISSION, TexType.TRANSMISSION)
        val transmission = if (transmissionFactor > 0f || transmissionTex != null) KhrTransmission(transmissionFactor, transmissionTex) else null
        var thicknessFactor = 0f
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_VOLUME_THICKNESS_FACTOR, 0, 0, f1, max) == aiReturn_SUCCESS) thicknessFactor = f1[0]
        var attenuationDistance = Float.MAX_VALUE
        max[0] = 1
        if (aiGetMaterialFloatArray(mat, AI_MATKEY_VOLUME_ATTENUATION_DISTANCE, 0, 0, f1, max) == aiReturn_SUCCESS) attenuationDistance = f1[0]
        val attenuationCol = AIColor4D.create()
        var attenuationColor = Vec3(1f, 1f, 1f)
        if (aiGetMaterialColor(mat, AI_MATKEY_VOLUME_ATTENUATION_COLOR, 0, 0, attenuationCol) == aiReturn_SUCCESS) attenuationColor = Vec3(attenuationCol.r(), attenuationCol.g(), attenuationCol.b())
        val volume = if (thicknessFactor > 0f) KhrVolume(thicknessFactor, attenuationDistance, attenuationColor, extractTexture(mat, aiTextureType_TRANSMISSION, TexType.THICKNESS)) else null
        var unlit = false
        val i1 = intArrayOf(0); val imax = intArrayOf(1)
        if (aiGetMaterialIntegerArray(mat, "\$mat.gltf.unlit", 0, 0, i1, imax) == aiReturn_SUCCESS) unlit = i1[0] != 0
        val unlitExt = if (unlit) KhrUnlit(true) else null
        val specularTex = extractTexture(mat, aiTextureType_SPECULAR, TexType.SPECULAR)
        val specularColorTex = extractTexture(mat, aiTextureType_MAYA_SPECULAR_COLOR, TexType.SPECULAR_COLOR)
        val specular = if (specularTex != null || specularColorTex != null) KhrSpecular(texture = specularTex, colorTexture = specularColorTex) else null
        val anisotropyTex = extractTexture(mat, aiTextureType_ANISOTROPY, TexType.ANISOTROPY)
        val anisotropy = if (anisotropyTex != null) KhrAnisotropy(texture = anisotropyTex) else null
        return KhrExt(
            clearcoat = clearcoat,
            sheen = sheen,
            transmission = transmission,
            volume = volume,
            specular = specular,
            anisotropy = anisotropy,
            emissiveStrength = KhrEmissiveStrength(),
            unlit = unlitExt
        )
    }
    private fun extractTexture(mat: AIMaterial, type: Int, texType: TexType): TexInfo? {
        val path = AIString.create()
        val uvIndex = intArrayOf(0); val blend = floatArrayOf(1f)
        if (aiGetMaterialTexture(mat, type, 0, path, null as IntArray?, uvIndex, blend, null, null, null) == aiReturn_SUCCESS) {
            val p = path.dataString()
            if (p.isNotEmpty()) return TexInfo(path = p, type = texType, uvIndex = uvIndex[0], uvTransform = extractUvTransform(mat, type))
        }
        return null
    }
    private fun extractUvTransform(mat: AIMaterial, type: Int): cn.chen.blaze3d.material.UVTransform {
        val uvTransform = AIUVTransform.create()
        var out = cn.chen.blaze3d.material.UVTransform()
        if (aiGetMaterialUVTransform(mat, _AI_MATKEY_UVTRANSFORM_BASE, type, 0, uvTransform) == aiReturn_SUCCESS) {
            val tr = uvTransform.mTranslation(); val sc = uvTransform.mScaling()
            out = cn.chen.blaze3d.material.UVTransform(offset = tr.x() to tr.y(), scale = sc.x() to sc.y(), rotation = uvTransform.mRotation())
        }
        return out
    }
    private fun insertBoneWeight(ids: IntArray, weights: FloatArray, id: Int, weight: Float) {
        if (weight <= weights[3]) return
        var slot = 3
        while (slot > 0 && weight > weights[slot - 1]) {
            weights[slot] = weights[slot - 1]
            ids[slot] = ids[slot - 1]
            slot--
        }
        weights[slot] = weight
        ids[slot] = id
    }
    private fun generateNormals(positions: AIVector3D.Buffer, numVerts: Int, indices: IntArray): List<Vec3> {
        val sums = ArrayList<Vec3>(numVerts).apply { repeat(numVerts) { add(Vec3()) } }
        var i = 0; val n = indices.size
        while (i + 2 < n) {
            val ia = indices[i]; val ib = indices[i + 1]; val ic = indices[i + 2]
            val a = positions.get(ia); val b = positions.get(ib); val c = positions.get(ic)
            val abx = b.x() - a.x(); val aby = b.y() - a.y(); val abz = b.z() - a.z()
            val acx = c.x() - a.x(); val acy = c.y() - a.y(); val acz = c.z() - a.z()
            val nx = aby * acz - abz * acy; val ny = abz * acx - abx * acz; val nz = abx * acy - aby * acx
            val sa = sums[ia]; sa.x += nx; sa.y += ny; sa.z += nz
            val sb = sums[ib]; sb.x += nx; sb.y += ny; sb.z += nz
            val sc = sums[ic]; sc.x += nx; sc.y += ny; sc.z += nz
            i += 3
        }
        for (k in 0 until numVerts) { val v = sums[k]; val l = v.length(); if (l > 0f) { val inv = 1f / l; v.x *= inv; v.y *= inv; v.z *= inv } else { v.x = 0f; v.y = 1f; v.z = 0f } }
        return sums
    }
    private fun parseMorphTargets(mesh: AIMesh, numVerts: Int): List<MorphTarget> {
        val numAnimMeshes = mesh.mNumAnimMeshes()
        if (numAnimMeshes == 0) return emptyList()
        val ptrs = mesh.mAnimMeshes() ?: return emptyList()
        return List(numAnimMeshes) { i ->
            val am = AIAnimMesh.create(ptrs.get(i))
            val pos = am.mVertices(); val norm = am.mNormals(); val tan = am.mTangents()
            val positions = if (pos != null) List(numVerts) { v -> val p = pos.get(v); Vec3(p.x(), p.y(), p.z()) } else emptyList()
            val normals = if (norm != null) List(numVerts) { v -> val n = norm.get(v); Vec3(n.x(), n.y(), n.z()) } else emptyList()
            val tangents = if (tan != null) List(numVerts) { v -> val t = tan.get(v); Vec3(t.x(), t.y(), t.z()) } else emptyList()
            MorphTarget(am.mName().dataString(), positions, normals, tangents)
        }
    }
    private fun parseAnimations(scene: AIScene): List<AnimClip> {
        val numAnims = scene.mNumAnimations()
        if (numAnims == 0) return emptyList()
        val animPtrs = scene.mAnimations() ?: return emptyList()
        return List(numAnims) { i -> parseAnimation(AIAnimation.create(animPtrs.get(i))) }
    }
    private fun parseAnimation(anim: AIAnimation): AnimClip {
        val numChannels = anim.mNumChannels()
        val channelPtrs = anim.mChannels() ?: return AnimClip(anim.mName().dataString(), anim.mDuration(), anim.mTicksPerSecond(), emptyList(), parseMeshAnimChannels(anim), parseMorphAnimChannels(anim))
        val channels = (0 until numChannels).map { i ->
            val channel = AINodeAnim.create(channelPtrs.get(i))
            val np = channel.mNumPositionKeys(); val nr = channel.mNumRotationKeys(); val ns = channel.mNumScalingKeys()
            val posTimes = DoubleArray(np); val posVals = FloatArray(np * 3)
            val rotTimes = DoubleArray(nr); val rotVals = FloatArray(nr * 4)
            val sclTimes = DoubleArray(ns); val sclVals = FloatArray(ns * 3)
            val pk = channel.mPositionKeys(); val rk = channel.mRotationKeys(); val sk = channel.mScalingKeys()
            if (pk != null) for (k in 0 until np) { val key = pk.get(k); posTimes[k] = key.mTime(); val v = key.mValue(); val o = k*3; posVals[o] = v.x(); posVals[o+1] = v.y(); posVals[o+2] = v.z() }
            if (rk != null) for (k in 0 until nr) { val key = rk.get(k); rotTimes[k] = key.mTime(); val v = key.mValue(); val o = k*4; rotVals[o] = v.x(); rotVals[o+1] = v.y(); rotVals[o+2] = v.z(); rotVals[o+3] = v.w() }
            if (sk != null) for (k in 0 until ns) { val key = sk.get(k); sclTimes[k] = key.mTime(); val v = key.mValue(); val o = k*3; sclVals[o] = v.x(); sclVals[o+1] = v.y(); sclVals[o+2] = v.z() }
            OurChannel(channel.mNodeName().dataString(), posTimes, posVals, rotTimes, rotVals, sclTimes, sclVals, parseAnimBehaviour(channel.mPreState()), parseAnimBehaviour(channel.mPostState()))
        }
        return AnimClip(anim.mName().dataString(), anim.mDuration(), anim.mTicksPerSecond(), channels, parseMeshAnimChannels(anim), parseMorphAnimChannels(anim))
    }
    private fun parseAnimBehaviour(value: Int) = when (value) {
        aiAnimBehaviour_CONSTANT -> AnimBehaviour.CONSTANT
        aiAnimBehaviour_LINEAR -> AnimBehaviour.LINEAR
        aiAnimBehaviour_REPEAT -> AnimBehaviour.REPEAT
        else -> AnimBehaviour.DEFAULT
    }
    private fun parseMeshAnimChannels(anim: AIAnimation): List<MeshAnimChannel> {
        val count = anim.mNumMeshChannels()
        if (count == 0) return emptyList()
        val ptrs = anim.mMeshChannels() ?: return emptyList()
        return (0 until count).map { i ->
            val ch = AIMeshAnim.create(ptrs.get(i))
            val numKeys = ch.mNumKeys()
            val times = DoubleArray(numKeys); val values = IntArray(numKeys)
            val keys = ch.mKeys()
            for (k in 0 until numKeys) { val key = keys.get(k); times[k] = key.mTime(); values[k] = key.mValue() }
            MeshAnimChannel(ch.mName().dataString(), times, values)
        }
    }
    private fun parseMorphAnimChannels(anim: AIAnimation): List<MorphAnimChannel> {
        val count = anim.mNumMorphMeshChannels()
        if (count == 0) return emptyList()
        val ptrs = anim.mMorphMeshChannels() ?: return emptyList()
        return (0 until count).map { i ->
            val ch = AIMeshMorphAnim.create(ptrs.get(i))
            val keys = ch.mKeys()
            val out = (0 until ch.mNumKeys()).map { k ->
                val key = keys.get(k)
                val len = key.mNumValuesAndWeights()
                val values = key.mValues(); val weights = key.mWeights()
                MorphKey(key.mTime(), IntArray(len) { values.get(it) }, FloatArray(len) { weights.get(it).toFloat() })
            }
            MorphAnimChannel(ch.mName().dataString(), out)
        }
    }
    private fun parseNode(node: AINode, parent: NodeGraph?): NodeGraph {
        val meshCount = node.mNumMeshes()
        val meshIndices = if (meshCount > 0) {
            val buf = node.mMeshes()
            if (buf == null) IntArray(0) else IntArray(meshCount) { buf.get(it) }
        } else IntArray(0)
        val graph = NodeGraph(
            name = node.mName().dataString(),
            transform = convertMatrix(node.mTransformation()),
            meshIndices = meshIndices,
            parent = parent
        )
        val numChildren = node.mNumChildren()
        if (numChildren > 0) {
            val childPtrs = node.mChildren() ?: return graph
            for (i in 0 until numChildren) {
                val child = AINode.create(childPtrs.get(i))
                graph.children.add(parseNode(child, graph))
            }
        }
        return graph
    }
    private fun parseEmbeddedTextures(scene: AIScene): List<EmbeddedTex> {
        val numTex = scene.mNumTextures()
        if (numTex == 0) return emptyList()
        val texPtrs = scene.mTextures() ?: return emptyList()
        return List(numTex) { i ->
            val tex = AITexture.create(texPtrs.get(i))
            val w = tex.mWidth(); val h = tex.mHeight()
            val data = ByteArray(if (h == 0) w else w * h * 4)
            if (h == 0) tex.pcDataCompressed().get(data) else {
                val texels = tex.pcData(); val total = w * h
                for (j in 0 until total) { val t = texels.get(j); val o = j * 4; data[o] = t.r(); data[o + 1] = t.g(); data[o + 2] = t.b(); data[o + 3] = t.a() }
            }
            EmbeddedTex(w, h, data, tex.achFormatHintString())
        }
    }
    private fun convertMatrix(m: AIMatrix4x4): Mat4 {
        return Mat4(floatArrayOf(
            m.a1(), m.a2(), m.a3(), m.a4(),
            m.b1(), m.b2(), m.b3(), m.b4(),
            m.c1(), m.c2(), m.c3(), m.c4(),
            m.d1(), m.d2(), m.d3(), m.d4()
        ))
    }
}
