package cn.chen.blaze3d.mmd
object MmdProfiles {
    private val hairKeys = listOf("hair", "髪", "髮", "前髪", "横髪", "後髪", "毛")
    private val skirtKeys = listOf("skirt", "スカート", "裙")
    private val clothKeys = listOf("cloth", "服", "袖", "裾", "cape", "マント")
    private val stageKeys = listOf("stage", "ステージ", "舞台", "scene", "场景", "場景", "floor", "床", "地面", "sky", "背景")
    fun buildChains(model: MmdModelMeta): List<MmdPhysicsChain> {
        val boneBodies = model.rigidBodies.mapIndexedNotNull { i, b -> if (b.boneIndex >= 0) b.boneIndex to i else null }.groupBy({ it.first }, { it.second })
        val bodyJoints = HashMap<Int, MutableList<Int>>()
        for ((i, j) in model.joints.withIndex()) {
            bodyJoints.getOrPut(j.rigidBodyA) { ArrayList() }.add(i)
            bodyJoints.getOrPut(j.rigidBodyB) { ArrayList() }.add(i)
        }
        val chains = ArrayList<MmdPhysicsChain>()
        for ((i, bone) in model.bones.withIndex()) {
            val type = chainType(bone.name, bone.englishName)
            if (type == MmdPhysicsChainType.BODY) continue
            val bones = collectChildren(model.bones, i)
            val bodies = bones.flatMap { boneBodies[it] ?: emptyList() }.distinct()
            val joints = bodies.flatMap { bodyJoints[it] ?: emptyList() }.distinct()
            if (bones.size > 1 || bodies.isNotEmpty()) chains.add(MmdPhysicsChain(bone.name, type, bones, bodies, joints))
        }
        return chains.distinctBy { it.name }
    }
    fun buildToonProfile(materials: List<MmdMaterialMeta>): MmdToonProfile {
        val toon = materials.map { it.toonTexture }.filter { it.isNotBlank() }.distinct()
        val sphere = materials.map { it.sphereTexture }.filter { it.isNotBlank() }.distinct()
        val outlines = materials.count { it.edgeSize > 0f || it.edgeColor.getOrElse(3) { 0f } > 0f }
        val transparentDouble = materials.any { it.doubleSided && it.edgeColor.getOrElse(3) { 1f } < 0.99f }
        return MmdToonProfile(toon.isNotEmpty() || outlines > 0, outlines > 0, transparentDouble, toon, sphere, outlines)
    }
    fun buildStageProfile(model: MmdModelMeta): MmdStageProfile {
        val text = listOf(model.name, model.englishName, model.comment, model.englishComment).joinToString(" ").lowercase()
        val stage = stageKeys.any { text.contains(it.lowercase()) } || model.bones.size < 16 && model.rigidBodies.isEmpty()
        val floor = text.contains("floor") || text.contains("床") || text.contains("地面")
        val backdrop = text.contains("背景") || text.contains("sky") || text.contains("back")
        val props = model.bones.count { b -> stageKeys.any { b.name.lowercase().contains(it.lowercase()) || b.englishName.lowercase().contains(it.lowercase()) } }
        return MmdStageProfile(stage, stage && model.rigidBodies.isEmpty(), backdrop, floor, props)
    }
    private fun chainType(name: String, english: String): MmdPhysicsChainType {
        val v = "$name $english".lowercase()
        return when {
            hairKeys.any { v.contains(it.lowercase()) } -> MmdPhysicsChainType.HAIR
            skirtKeys.any { v.contains(it.lowercase()) } -> MmdPhysicsChainType.SKIRT
            clothKeys.any { v.contains(it.lowercase()) } -> MmdPhysicsChainType.CLOTH
            else -> MmdPhysicsChainType.BODY
        }
    }
    private fun collectChildren(bones: List<MmdBoneMeta>, root: Int): List<Int> {
        val out = ArrayList<Int>()
        fun walk(i: Int) {
            out.add(i)
            for ((child, bone) in bones.withIndex()) if (bone.parentIndex == i) walk(child)
        }
        walk(root)
        return out
    }
}
