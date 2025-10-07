package top.fifthlight.blazerod.runtime.resource

import org.joml.Vector3fc
import top.fifthlight.blazerod.model.PhysicalJoint

data class RenderPhysicsJoint(
    val name: String? = null,
    val type: PhysicalJoint.JointType,
    val rigidBodyAIndex: Int,
    val rigidBodyBIndex: Int,
    val position: Vector3fc,
    val rotation: Vector3fc,
    val positionMin: Vector3fc,
    val positionMax: Vector3fc,
    val rotationMin: Vector3fc,
    val rotationMax: Vector3fc,
    val positionSpring: Vector3fc,
    val rotationSpring: Vector3fc,
)
