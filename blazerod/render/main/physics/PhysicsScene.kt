package top.fifthlight.blazerod.physics

import org.joml.Vector3fc
import top.fifthlight.blazerod.model.RigidBody
import top.fifthlight.blazerod.runtime.resource.RenderPhysicsJoint
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhysicsScene(
    rigidBodies: List<RigidBody>,
    joints: List<RenderPhysicsJoint>,
) : AutoCloseable {
    private val pointer: Long
    private var closed = false

    init {
        require(rigidBodies.isNotEmpty()) { "Rigidbodies must not be empty" }

        if (!PhysicsLibrary.isPhysicsAvailable()) {
            throw IllegalStateException("Physics library is not available")
        }

        fun ByteBuffer.putVector3f(offset: Int, vector: Vector3fc) = this
            .putFloat(offset + 0, vector.x())
            .putFloat(offset + 4, vector.y())
            .putFloat(offset + 8, vector.z())

        val rigidBodyItemSize = 72
        val rigidBodiesBuffer = ByteBuffer.allocateDirect(rigidBodyItemSize * rigidBodies.size)
            .order(ByteOrder.nativeOrder())
        rigidBodies.forEachIndexed { index, rigidBody ->
            val offset = index * rigidBodyItemSize
            rigidBodiesBuffer.putInt(offset + 0, rigidBody.collisionGroup)
            rigidBodiesBuffer.putInt(offset + 4, rigidBody.collisionMask)
            rigidBodiesBuffer.putInt(offset + 8, rigidBody.shape.ordinal)
            rigidBodiesBuffer.putInt(offset + 12, rigidBody.physicsMode.ordinal)
            rigidBodiesBuffer.putVector3f(offset + 16, rigidBody.shapeSize)
            rigidBodiesBuffer.putVector3f(offset + 28, rigidBody.shapePosition)
            rigidBodiesBuffer.putVector3f(offset + 40, rigidBody.shapeRotation)
            rigidBodiesBuffer.putFloat(offset + 52, rigidBody.mass)
            rigidBodiesBuffer.putFloat(offset + 56, rigidBody.moveAttenuation)
            rigidBodiesBuffer.putFloat(offset + 60, rigidBody.rotationDamping)
            rigidBodiesBuffer.putFloat(offset + 64, rigidBody.repulsion)
            rigidBodiesBuffer.putFloat(offset + 68, rigidBody.frictionForce)
        }

        val jointItemSize = 108
        val jointsBuffer = ByteBuffer.allocateDirect(jointItemSize * joints.size)
            .order(ByteOrder.nativeOrder())
        joints.forEachIndexed { index, joint ->
            val offset = index * jointItemSize
            jointsBuffer.putInt(offset + 0, joint.type.ordinal)
            jointsBuffer.putInt(offset + 4, joint.rigidBodyAIndex)
            jointsBuffer.putInt(offset + 8, joint.rigidBodyBIndex)
            jointsBuffer.putVector3f(offset + 12, joint.position)
            jointsBuffer.putVector3f(offset + 24, joint.rotation)
            jointsBuffer.putVector3f(offset + 36, joint.positionMin)
            jointsBuffer.putVector3f(offset + 48, joint.positionMax)
            jointsBuffer.putVector3f(offset + 60, joint.rotationMin)
            jointsBuffer.putVector3f(offset + 72, joint.rotationMax)
            jointsBuffer.putVector3f(offset + 84, joint.positionSpring)
            jointsBuffer.putVector3f(offset + 96, joint.rotationSpring)
        }

        try {
            pointer = PhysicsLibrary.createPhysicsScene(rigidBodiesBuffer, jointsBuffer)
        } finally {
            Reference.reachabilityFence(rigidBodiesBuffer)
            Reference.reachabilityFence(jointsBuffer)
        }
    }

    internal fun getPointer(): Long {
        require(!closed) { "PhysicsScene is closed" }
        return pointer
    }

    override fun close() {
        if (closed) {
            return
        }
        PhysicsLibrary.destroyPhysicsScene(pointer)
        closed = true
    }
}
