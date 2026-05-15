package cn.chen.blaze3d.mmd
class MmdStagePlayback {
    var enabled = false
    var stagePath = ""
    var cameraMotion = ""
    var lightEnabled = true
    var shadowEnabled = true
    var frame = 0
    var playing = false
    fun load(project: MmdProjectConfig?) {
        if (project == null) return
        stagePath = project.stage
        enabled = stagePath.isNotBlank()
        playing = enabled
    }
    fun update(seconds: Double, runtime: MmdRuntime) {
        if (!enabled || !playing) return
        frame = (seconds * 30.0).toInt()
        runtime.stageFrame = frame
    }
    fun status() = "stage=$enabled playing=$playing path=$stagePath camera=$cameraMotion light=$lightEnabled shadow=$shadowEnabled frame=$frame"
}
class MmdEditorState {
    var selectedProject = ""
    var selectedBone = ""
    var selectedMorph = ""
    var selectedMaterial = ""
    var timelineFrame = 0
    var timelineFps = 30
    var saveName = "project.json"
    var resourceFilter = ""
    fun status() = "project=$selectedProject frame=$timelineFrame fps=$timelineFps bone=$selectedBone morph=$selectedMorph material=$selectedMaterial save=$saveName filter=$resourceFilter"
}
