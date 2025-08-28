package top.fifthlight.blazerod.util

import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3fc

internal fun Matrix4fc.rotateYXZ(angles: Vector3fc, dst: Matrix4f): Matrix4f =
    rotateYXZ(angles.y(), angles.x(), angles.z(), dst)

internal fun Matrix4f.rotateYXZ(angles: Vector3fc) = rotateYXZ(angles, this)

internal fun Quaternionf.rotationZYX(angles: Vector3fc) = rotationZYX(angles.z(), angles.y(), angles.x())
