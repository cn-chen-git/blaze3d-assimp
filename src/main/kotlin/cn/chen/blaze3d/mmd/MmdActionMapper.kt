package cn.chen.blaze3d.mmd
import net.minecraft.client.player.LocalPlayer
enum class MmdAction { IDLE, WALK, SPRINT, SNEAK, SWING_RIGHT, SWING_LEFT, ELYTRA_FLY, SWIM, CLIMB, CLIMB_UP, CLIMB_DOWN, SLEEP, RIDE, DIE, CRAWL, LIE_DOWN, HURT, FALL, JUMP }
class MmdActionMapper {
    var enabled = true
    var current = MmdAction.IDLE; private set
    var motionName = "idle"; private set
    var switches = 0; private set
    private var lastY = 0.0
    private var lastConfig: MmdProjectConfig? = null
    private var lastMotionMap: Map<String, String>? = null
    private val defaults = mapOf(
        MmdAction.IDLE to "idle", MmdAction.WALK to "walk", MmdAction.SPRINT to "sprint", MmdAction.SNEAK to "sneak",
        MmdAction.SWING_RIGHT to "swingRight", MmdAction.SWING_LEFT to "swingLeft", MmdAction.ELYTRA_FLY to "elytraFly",
        MmdAction.SWIM to "swim", MmdAction.CLIMB to "onClimbable", MmdAction.CLIMB_UP to "onClimbableUp",
        MmdAction.CLIMB_DOWN to "onClimbableDown", MmdAction.SLEEP to "sleep", MmdAction.RIDE to "ride",
        MmdAction.DIE to "die", MmdAction.CRAWL to "crawl", MmdAction.LIE_DOWN to "lieDown", MmdAction.HURT to "hurt",
        MmdAction.FALL to "fall", MmdAction.JUMP to "jump"
    )
    fun update(player: LocalPlayer?, config: MmdProjectConfig?): Boolean {
        if (!enabled || player == null) return false
        val next = detect(player)
        val changed = next != current
        if (changed) {
            current = next
            switches++
        }
        if (config !== lastConfig) { lastConfig = config; lastMotionMap = config?.actionMotions }
        motionName = lastMotionMap?.get(next.name.lowercase()) ?: defaults[next] ?: "idle"
        lastY = player.y
        return changed
    }
    private fun detect(p: LocalPlayer): MmdAction {
        val dy = p.y - lastY
        val moving = p.deltaMovement.horizontalDistanceSqr() > 0.0004
        return when {
            p.isDeadOrDying -> MmdAction.DIE
            p.isSleeping -> MmdAction.SLEEP
            p.isPassenger -> MmdAction.RIDE
            p.isFallFlying -> MmdAction.ELYTRA_FLY
            p.isSwimming -> MmdAction.SWIM
            p.isCrouching && p.onGround() && moving -> MmdAction.SNEAK
            p.isCrouching && !p.onGround() -> MmdAction.CRAWL
            p.onClimbable() && dy > 0.01 -> MmdAction.CLIMB_UP
            p.onClimbable() && dy < -0.01 -> MmdAction.CLIMB_DOWN
            p.onClimbable() -> MmdAction.CLIMB
            p.hurtTime > 0 -> MmdAction.HURT
            !p.onGround() && p.deltaMovement.y > 0.02 -> MmdAction.JUMP
            !p.onGround() && p.deltaMovement.y < -0.08 -> MmdAction.FALL
            p.swinging && (p.swingingArm?.name ?: "").lowercase().contains("left") -> MmdAction.SWING_LEFT
            p.swinging -> MmdAction.SWING_RIGHT
            p.isSprinting && moving -> MmdAction.SPRINT
            moving -> MmdAction.WALK
            else -> MmdAction.IDLE
        }
    }
}
