package cn.chen.blaze3d.physics
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.core.Transform
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsGhostObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
class PhysicsBinding {
    var enabled = true
    var entityCollision = true
    var shape: PhysicsShape? = null
        private set
    var overlapCount = 0
        private set
    var bodyCount = 0
        private set
    var mass = 0f
        private set
    var mmdChainCount = 0
        private set
    var mmdDynamicBodies = 0
        private set
    private var body: PhysicsRigidBody? = null
    private var ghost: PhysicsGhostObject? = null
    private val tmpLoc = com.jme3.math.Vector3f()
    private val tmpRot = Quaternion()
    private var lastX = Float.NaN; private var lastY = Float.NaN; private var lastZ = Float.NaN; private var lastScale = Float.NaN
    private var lastRx = Float.NaN; private var lastRy = Float.NaN; private var lastRz = Float.NaN
    fun bind(scene: SceneData) {
        clear()
        var vertices = 0; var indices = 0
        var mnx = Float.MAX_VALUE; var mny = Float.MAX_VALUE; var mnz = Float.MAX_VALUE
        var mxx = -Float.MAX_VALUE; var mxy = -Float.MAX_VALUE; var mxz = -Float.MAX_VALUE
        for (mesh in scene.meshes) {
            vertices += mesh.vertexCount; indices += mesh.indexCount
            for (v in mesh.vertices) {
                val p = v.position
                if (p.x < mnx) mnx = p.x; if (p.y < mny) mny = p.y; if (p.z < mnz) mnz = p.z
                if (p.x > mxx) mxx = p.x; if (p.y > mxy) mxy = p.y; if (p.z > mxz) mxz = p.z
            }
        }
        shape = PhysicsShape(scene.meshes.size, vertices, indices, Vector3f(mnx, mny, mnz), Vector3f(mxx, mxy, mxz))
        createBulletBody()
    }
    fun bindMmd(chainCount: Int, dynamicBodies: Int) {
        mmdChainCount = chainCount
        mmdDynamicBodies = dynamicBodies
    }
    fun sync(instance: Transform) {
        if (!enabled) return
        val s = shape ?: return
        val p = instance.pos; val r = instance.rot; val sc = instance.scale
        if (p.x == lastX && p.y == lastY && p.z == lastZ && sc == lastScale && r.x == lastRx && r.y == lastRy && r.z == lastRz) return
        lastX = p.x; lastY = p.y; lastZ = p.z; lastScale = sc; lastRx = r.x; lastRy = r.y; lastRz = r.z
        val c = s.center
        tmpLoc.set(p.x + c.x * sc, p.y + c.y * sc, p.z + c.z * sc)
        tmpRot.fromAngles(Math.toRadians(r.x.toDouble()).toFloat(), Math.toRadians(r.y.toDouble()).toFloat(), Math.toRadians(r.z.toDouble()).toFloat())
        body?.apply { setPhysicsLocation(tmpLoc); setPhysicsRotation(tmpRot) }
        ghost?.apply { setPhysicsLocation(tmpLoc); setPhysicsRotation(tmpRot) }
        overlapCount = ghost?.overlappingCount ?: 0
    }
    fun collideEntities(instance: Transform, entities: List<Entity>) {
        if (!enabled || !entityCollision) return
        val box = shape?.box(instance.pos, instance.scale) ?: return
        val center = box.getCenter(); val cxd = center.x; val czd = center.z
        var count = 0
        for (entity in entities) {
            if (!entity.getBoundingBox().intersects(box)) continue
            count++
            val pos = entity.position(); val dx = pos.x - cxd; val dz = pos.z - czd
            val len = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
            entity.push(Vec3(dx / len * 0.08, 0.02, dz / len * 0.08))
        }
        overlapCount = maxOf(overlapCount, count)
    }
    private fun createBulletBody() {
        BulletWorld.init()
        val world = BulletWorld.space ?: return
        val s = shape ?: return
        val ext = s.halfExtents
        val collisionShape = BoxCollisionShape(com.jme3.math.Vector3f(ext.x.coerceAtLeast(0.01f), ext.y.coerceAtLeast(0.01f), ext.z.coerceAtLeast(0.01f)))
        body = PhysicsRigidBody(collisionShape, 0f)
        ghost = PhysicsGhostObject(collisionShape)
        body?.setKinematic(true)
        body?.let { world.addCollisionObject(it) }
        ghost?.let { world.addCollisionObject(it) }
        mass = body?.mass ?: 0f
        bodyCount = world.countRigidBodies()
    }
    fun clear() {
        val world = BulletWorld.space
        body?.let { world?.removeCollisionObject(it) }
        ghost?.let { world?.removeCollisionObject(it) }
        body = null
        ghost = null
        overlapCount = 0
        bodyCount = world?.countRigidBodies() ?: 0
        mass = 0f
        shape = null
        mmdChainCount = 0
        mmdDynamicBodies = 0
        lastX = Float.NaN; lastY = Float.NaN; lastZ = Float.NaN; lastScale = Float.NaN; lastRx = Float.NaN; lastRy = Float.NaN; lastRz = Float.NaN
    }
}
