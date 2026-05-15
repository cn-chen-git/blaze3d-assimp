package cn.chen.blaze3d.render
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.exp
class CascadeSplitter(val cascadeCount: Int = 4, val lambda: Float = 0.6f) {
    fun split(near: Float, far: Float): FloatArray {
        val splits = FloatArray(cascadeCount + 1)
        splits[0] = near; splits[cascadeCount] = far
        for (i in 1 until cascadeCount) {
            val si = i.toFloat() / cascadeCount
            val log = near * Math.pow((far / near).toDouble(), si.toDouble()).toFloat()
            val uniform = near + (far - near) * si
            splits[i] = lambda * log + (1f - lambda) * uniform
        }
        return splits
    }
}
data class CascadeFrustumCorners(val corners: Array<Vector3f>) {
    val center: Vector3f get() { val c = Vector3f(); for (corner in corners) c.add(corner); return c.div(corners.size.toFloat()) }
    val radius: Float get() { val c = center; var r = 0f; for (corner in corners) { val d = c.distance(corner); if (d > r) r = d }; return r }
}
class CascadeMatrixBuilder {
    fun buildLightView(lightDir: Vector3f, target: Vector3f, up: Vector3f = Vector3f(0f, 1f, 0f)): Matrix4f {
        val eye = Vector3f(target).sub(lightDir)
        return Matrix4f().lookAt(eye, target, up)
    }
    fun buildOrtho(corners: CascadeFrustumCorners, light: Vector3f): Matrix4f {
        val view = buildLightView(light, corners.center)
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        val transformed = Vector4f()
        for (corner in corners.corners) {
            transformed.set(corner.x, corner.y, corner.z, 1f).mul(view)
            if (transformed.x < minX) minX = transformed.x; if (transformed.x > maxX) maxX = transformed.x
            if (transformed.y < minY) minY = transformed.y; if (transformed.y > maxY) maxY = transformed.y
            if (transformed.z < minZ) minZ = transformed.z; if (transformed.z > maxZ) maxZ = transformed.z
        }
        val tightness = 10f
        if (minZ < 0f) minZ *= tightness else minZ /= tightness
        if (maxZ < 0f) maxZ /= tightness else maxZ *= tightness
        return Matrix4f().ortho(minX, maxX, minY, maxY, -maxZ, -minZ)
    }
    fun buildFrustumCorners(view: Matrix4f, projection: Matrix4f, nearSplit: Float, farSplit: Float, fovDegrees: Float, aspect: Float): CascadeFrustumCorners {
        val tanH = tan(Math.toRadians(fovDegrees.toDouble()).toFloat() * 0.5f)
        val nearH = nearSplit * tanH; val nearW = nearH * aspect
        val farH = farSplit * tanH; val farW = farH * aspect
        val invView = Matrix4f(view).invert()
        val corners = Array(8) { idx ->
            val isFar = idx >= 4
            val z = if (isFar) -farSplit else -nearSplit
            val w = if (isFar) farW else nearW
            val h = if (isFar) farH else nearH
            val x = if (idx % 2 == 0) -w else w
            val y = if (idx / 2 % 2 == 0) -h else h
            val v = Vector4f(x, y, z, 1f).mul(invView)
            Vector3f(v.x / v.w, v.y / v.w, v.z / v.w)
        }
        return CascadeFrustumCorners(corners)
    }
}
data class CascadeData(val viewProjection: Matrix4f, val splitFar: Float, val texelSize: Float, val depthBias: Float)
class CascadedShadowMap(val cascadeCount: Int = 4, val resolution: Int = 1024) {
    val splitter = CascadeSplitter(cascadeCount)
    val matrices = CascadeMatrixBuilder()
    val cascades = ArrayList<CascadeData>(cascadeCount)
    private val lightDir = Vector3f(0.3f, 1f, 0.4f).normalize()
    fun setLightDirection(dir: Vector3f) { lightDir.set(dir).normalize() }
    fun update(view: Matrix4f, projection: Matrix4f, near: Float, far: Float, fovDegrees: Float, aspect: Float) {
        cascades.clear()
        val splits = splitter.split(near, far)
        for (i in 0 until cascadeCount) {
            val corners = matrices.buildFrustumCorners(view, projection, splits[i], splits[i + 1], fovDegrees, aspect)
            val lightProj = matrices.buildOrtho(corners, lightDir)
            val lightView = matrices.buildLightView(lightDir, corners.center)
            val viewProjection = Matrix4f(lightProj).mul(lightView)
            val texelSize = 2f * corners.radius / resolution
            val depthBias = 0.0025f * (1f + i)
            cascades.add(CascadeData(viewProjection, splits[i + 1], texelSize, depthBias))
        }
    }
    fun cascadeIndexFor(distance: Float): Int {
        for (i in 0 until cascades.size) if (distance <= cascades[i].splitFar) return i
        return cascades.size - 1
    }
    fun status() = "csm cascades=${cascades.size} resolution=$resolution lambda=${splitter.lambda}"
}
class ShadowCaster(val csm: CascadedShadowMap) {
    private val tmpCenter = Vector3f()
    fun shouldCast(worldCenter: Vector3f, cameraPos: Vector3f, radius: Float, cascade: Int): Boolean {
        tmpCenter.set(worldCenter).sub(cameraPos)
        val distance = tmpCenter.length()
        val data = csm.cascades.getOrNull(cascade) ?: return false
        return distance - radius < data.splitFar
    }
    fun pcfFilterSize(cascade: Int): Float {
        val data = csm.cascades.getOrNull(cascade) ?: return 1f
        return kotlin.math.max(1f, 1f / (data.texelSize * 1024f))
    }
}
