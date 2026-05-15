package cn.chen.blaze3d.render
class PerformanceConfig {
    var vulkanBatching = true
    var gpuSkinningCache = true
    var materialSorting = true
    var textureAtlas = true
    var asyncTextureUpload = true
    var autoLod = true
    var lodBias = 1f
    var gpuSkinningCacheHits = 0
    var sortedMaterials = 0
    var atlasTextures = 0
    var asyncUploads = 0
    var skippedBoneUploads = 0
    var uploadedBoneFrames = 0
    var culledFrames = 0
    var renderedFrames = 0
    fun status() = "vulkanBatching=$vulkanBatching gpuSkinningCache=$gpuSkinningCache hits=$gpuSkinningCacheHits materialSorting=$materialSorting sorted=$sortedMaterials textureAtlas=$textureAtlas atlas=$atlasTextures asyncTextureUpload=$asyncTextureUpload uploads=$asyncUploads autoLod=$autoLod lodBias=$lodBias boneSkip=$skippedBoneUploads boneUpload=$uploadedBoneFrames culled=$culledFrames rendered=$renderedFrames"
}
