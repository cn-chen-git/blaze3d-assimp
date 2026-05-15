package cn.chen.blaze3d.physics
import com.jme3.math.Vector3f
import org.joml.Vector3f as JVec3
import kotlin.math.max
import kotlin.math.sqrt
data class SoftbodyParticle(val mass: Float, var pinned: Boolean) {
    val position: JVec3 = JVec3()
    val previousPosition: JVec3 = JVec3()
    val acceleration: JVec3 = JVec3()
    val anchor: JVec3 = JVec3()
}
data class SoftbodyConstraint(val a: Int, val b: Int, val restLength: Float, val stiffness: Float, val isBend: Boolean = false)
abstract class SoftbodyBase {
    val particles: ArrayList<SoftbodyParticle> = ArrayList()
    val constraints: ArrayList<SoftbodyConstraint> = ArrayList()
    var iterations: Int = 16
    var damping: Float = 0.985f
    var gravity: JVec3 = JVec3(0f, -9.81f, 0f)
    var windDirection: JVec3 = JVec3(1f, 0f, 0f)
    var windStrength: Float = 0f
    var collisionRadius: Float = 0.02f
    fun addParticle(position: JVec3, mass: Float = 1f, pinned: Boolean = false) {
        val particle = SoftbodyParticle(mass, pinned)
        particle.position.set(position); particle.previousPosition.set(position); particle.anchor.set(position)
        particles.add(particle)
    }
    fun addConstraint(a: Int, b: Int, stiffness: Float, isBend: Boolean = false) {
        val pa = particles[a]; val pb = particles[b]
        val rest = pa.position.distance(pb.position)
        constraints.add(SoftbodyConstraint(a, b, rest, stiffness, isBend))
    }
    fun applyForce(force: JVec3) { for (p in particles) if (!p.pinned) p.acceleration.add(force) }
    fun applyImpulse(direction: JVec3, magnitude: Float) { for (p in particles) if (!p.pinned) p.position.add(JVec3(direction).mul(magnitude)) }
    fun step(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        integrate(dt)
        for (i in 0 until iterations) solveConstraints()
        collideWorld()
    }
    private fun integrate(dt: Float) {
        val gravityAcc = JVec3(gravity)
        val windAcc = JVec3(windDirection).normalize().mul(windStrength)
        for (p in particles) {
            if (p.pinned) { p.position.set(p.anchor); continue }
            val velocity = JVec3(p.position).sub(p.previousPosition).mul(damping)
            p.previousPosition.set(p.position)
            p.acceleration.add(gravityAcc).add(windAcc)
            p.position.add(velocity).add(JVec3(p.acceleration).mul(dt * dt))
            p.acceleration.set(0f, 0f, 0f)
        }
    }
    private fun solveConstraints() {
        for (constraint in constraints) {
            val pa = particles[constraint.a]; val pb = particles[constraint.b]
            val delta = JVec3(pb.position).sub(pa.position)
            val dist = max(delta.length(), 1e-5f)
            val error = (dist - constraint.restLength) / dist
            val k = if (constraint.isBend) constraint.stiffness * 0.5f else constraint.stiffness
            val correction = JVec3(delta).mul(0.5f * k * error)
            if (!pa.pinned) pa.position.add(correction)
            if (!pb.pinned) pb.position.sub(correction)
        }
    }
    private fun collideWorld() {
        val space = BulletWorld.space ?: return
        val tmp = Vector3f()
        for (p in particles) {
            if (p.pinned) continue
            tmp.set(p.position.x, p.position.y, p.position.z)
        }
    }
}
class ClothSimulation(val widthSegments: Int, val heightSegments: Int, val cellSize: Float) : SoftbodyBase() {
    init { build() }
    private fun build() {
        for (y in 0..heightSegments) for (x in 0..widthSegments) {
            val pos = JVec3(x.toFloat() * cellSize, -y.toFloat() * cellSize, 0f)
            val pinned = y == 0 && (x == 0 || x == widthSegments)
            addParticle(pos, mass = 1f, pinned = pinned)
        }
        val w = widthSegments + 1
        for (y in 0..heightSegments) for (x in 0..widthSegments) {
            val idx = y * w + x
            if (x < widthSegments) addConstraint(idx, idx + 1, 0.95f)
            if (y < heightSegments) addConstraint(idx, idx + w, 0.95f)
            if (x < widthSegments && y < heightSegments) {
                addConstraint(idx, idx + w + 1, 0.65f, isBend = true)
                addConstraint(idx + 1, idx + w, 0.65f, isBend = true)
            }
            if (x < widthSegments - 1) addConstraint(idx, idx + 2, 0.4f, isBend = true)
            if (y < heightSegments - 1) addConstraint(idx, idx + 2 * w, 0.4f, isBend = true)
        }
    }
    fun pin(x: Int, y: Int) { particles[y * (widthSegments + 1) + x].pinned = true }
    fun unpin(x: Int, y: Int) { particles[y * (widthSegments + 1) + x].pinned = false }
    fun setAnchor(x: Int, y: Int, position: JVec3) { val idx = y * (widthSegments + 1) + x; particles[idx].anchor.set(position); if (particles[idx].pinned) particles[idx].position.set(position) }
    fun particleAt(x: Int, y: Int): SoftbodyParticle = particles[y * (widthSegments + 1) + x]
}
class HairStrand(val segments: Int, val segmentLength: Float, val mass: Float = 0.1f) : SoftbodyBase() {
    init { build(); damping = 0.92f }
    private fun build() {
        for (i in 0..segments) {
            val pos = JVec3(0f, -i * segmentLength, 0f)
            addParticle(pos, mass, pinned = i == 0)
        }
        for (i in 0 until segments) {
            addConstraint(i, i + 1, stiffness = 1f)
            if (i < segments - 1) addConstraint(i, i + 2, stiffness = 0.4f, isBend = true)
        }
    }
    fun setRoot(position: JVec3) { particles[0].anchor.set(position); particles[0].position.set(position) }
}
class HairSimulation(val rootPositions: List<JVec3>, val segmentsPerStrand: Int = 12, val segmentLength: Float = 0.05f) {
    val strands: ArrayList<HairStrand> = ArrayList(rootPositions.size)
    init { for (root in rootPositions) { val strand = HairStrand(segmentsPerStrand, segmentLength); strand.setRoot(root); strands.add(strand) } }
    fun step(deltaSeconds: Float) { for (strand in strands) strand.step(deltaSeconds) }
    fun applyGravity(gravity: JVec3) { for (strand in strands) strand.gravity.set(gravity) }
    fun applyWind(direction: JVec3, strength: Float) { for (strand in strands) { strand.windDirection.set(direction); strand.windStrength = strength } }
    fun setRoot(strandIndex: Int, position: JVec3) { strands.getOrNull(strandIndex)?.setRoot(position) }
    fun strandPositions(strandIndex: Int): List<JVec3>? = strands.getOrNull(strandIndex)?.particles?.map { it.position }
}
class SoftbodyManager {
    private val cloths: ArrayList<ClothSimulation> = ArrayList()
    private val hairs: ArrayList<HairSimulation> = ArrayList()
    fun registerCloth(simulation: ClothSimulation): ClothSimulation { cloths.add(simulation); return simulation }
    fun registerHair(simulation: HairSimulation): HairSimulation { hairs.add(simulation); return simulation }
    fun step(deltaSeconds: Float) {
        val safeDelta = deltaSeconds.coerceIn(0f, 0.05f)
        for (cloth in cloths) cloth.step(safeDelta)
        for (hair in hairs) hair.step(safeDelta)
    }
    fun status() = "softbody cloth=${cloths.size} hair=${hairs.size} particles=${cloths.sumOf { it.particles.size } + hairs.sumOf { hair -> hair.strands.sumOf { it.particles.size } }}"
}
