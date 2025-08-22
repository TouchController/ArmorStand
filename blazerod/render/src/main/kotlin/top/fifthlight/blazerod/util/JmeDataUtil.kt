package top.fifthlight.blazerod.util

import com.jme3.math.Transform
import org.joml.*

typealias JmeVector3f = com.jme3.math.Vector3f

fun Vector3fc.get(jmeVector3f: JmeVector3f): JmeVector3f = jmeVector3f.set(x(), y(), z())
fun Vector3f.set(jmeVector3f: JmeVector3f): Vector3f = set(jmeVector3f.x, jmeVector3f.y, jmeVector3f.z)
fun JmeVector3f.get(vector3f: Vector3f): Vector3f = vector3f.set(x, y, z)
fun JmeVector3f.set(vector3f: Vector3fc): JmeVector3f = this.set(vector3f.x(), vector3f.y(), vector3f.z())
fun Vector3fc.toJme() = JmeVector3f(x(), y(), z())

typealias JmeMatrix4f = com.jme3.math.Matrix4f

fun Matrix4fc.get(jmeMatrix4f: JmeMatrix4f) = jmeMatrix4f.apply {
    m00 = m00()
    m01 = m10()
    m02 = m20()
    m03 = m30()
    m10 = m01()
    m11 = m11()
    m12 = m21()
    m13 = m31()
    m20 = m02()
    m21 = m12()
    m22 = m22()
    m23 = m32()
    m30 = m03()
    m31 = m13()
    m32 = m23()
    m33 = m33()
}

fun Matrix4f.set(jmeMatrix4f: JmeMatrix4f) = apply {
    m00(jmeMatrix4f.m00)
    m01(jmeMatrix4f.m10)
    m02(jmeMatrix4f.m20)
    m03(jmeMatrix4f.m30)
    m10(jmeMatrix4f.m01)
    m11(jmeMatrix4f.m11)
    m12(jmeMatrix4f.m21)
    m13(jmeMatrix4f.m31)
    m20(jmeMatrix4f.m02)
    m21(jmeMatrix4f.m12)
    m22(jmeMatrix4f.m22)
    m23(jmeMatrix4f.m32)
    m30(jmeMatrix4f.m03)
    m31(jmeMatrix4f.m13)
    m32(jmeMatrix4f.m23)
    m33(jmeMatrix4f.m33)
}

fun JmeMatrix4f.get(matrix4f: Matrix4f) = matrix4f.set(this)
fun JmeMatrix4f.set(matrix4fc: Matrix4fc) = matrix4fc.get(this)
fun Matrix4fc.toJme() = JmeMatrix4f().set(this)

typealias JmeMatrix3f = com.jme3.math.Matrix3f

typealias JmeQuaternion = com.jme3.math.Quaternion

fun Quaternionfc.get(jmeQuaternion: JmeQuaternion): JmeQuaternion = jmeQuaternion.set(x(), y(), z(), w())
fun Quaternionf.set(jmeQuaternion: JmeQuaternion): Quaternionf =
    set(jmeQuaternion.x, jmeQuaternion.y, jmeQuaternion.z, jmeQuaternion.w)

fun JmeQuaternion.get(quaternionf: Quaternionf): Quaternionf = quaternionf.set(x, y, z, w)
fun JmeQuaternion.set(quaternionf: Quaternionfc): JmeQuaternion =
    this.set(quaternionf.x(), quaternionf.y(), quaternionf.z(), quaternionf.w())

fun Quaternionfc.toJme() = JmeQuaternion().set(this)

fun Matrix4f.set(transform: Transform) = transform.get(this)
fun Transform.get(matrix4f: Matrix4f) = matrix4f.translationRotateScale(
    translation.x,
    translation.y,
    translation.z,
    rotation.x,
    rotation.y,
    rotation.z,
    rotation.w,
    scale.x,
    scale.y,
    scale.z
)
