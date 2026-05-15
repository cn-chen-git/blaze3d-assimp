package cn.chen.blaze3d.mmd
import cn.chen.blaze3d.core.AnimChannel
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.MorphAnimChannel
import cn.chen.blaze3d.core.MorphKey
object MmdMotionCompiler {
    private const val FPS = 30.0
    fun compile(motion: MmdMotion): AnimClip {
        val channels = motion.bones.groupBy { it.boneName }.map { (bone, frames) ->
            val sorted = frames.sortedBy { it.frame }
            val posTimes = DoubleArray(sorted.size)
            val posVals = FloatArray(sorted.size * 3)
            val rotTimes = DoubleArray(sorted.size)
            val rotVals = FloatArray(sorted.size * 4)
            val sclTimes = DoubleArray(sorted.size)
            val sclVals = FloatArray(sorted.size * 3)
            for ((i, f) in sorted.withIndex()) {
                val t = f.frame / FPS
                posTimes[i] = t; rotTimes[i] = t; sclTimes[i] = t
                val p = i * 3; posVals[p] = f.position.x; posVals[p + 1] = f.position.y; posVals[p + 2] = -f.position.z; sclVals[p] = 1f; sclVals[p + 1] = 1f; sclVals[p + 2] = 1f
                val r = i * 4; rotVals[r] = -f.rotation.x; rotVals[r + 1] = -f.rotation.y; rotVals[r + 2] = f.rotation.z; rotVals[r + 3] = f.rotation.w
            }
            AnimChannel(bone, posTimes, posVals, rotTimes, rotVals, sclTimes, sclVals)
        }
        val morphs = motion.morphs.groupBy { it.morphName }.map { (name, frames) ->
            val keys = frames.sortedBy { it.frame }.map { MorphKey(it.frame / FPS, intArrayOf(0), floatArrayOf(it.weight)) }
            MorphAnimChannel(name, keys)
        }
        return AnimClip(motion.name, motion.durationFrame.toDouble(), FPS, channels, emptyList(), morphs)
    }
}
