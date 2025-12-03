package top.fifthlight.blazerod.physics

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.AutoCloseable
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*

class PhysicsWorld(
    scene: PhysicsScene,
    initialTransform: ByteBuffer,
) : AutoCloseable {
    private val pointer: Long
    private var closed = false
    internal val rigidBodyCount = scene.rigidBodyCount
    private val transformBuffer: ByteBuffer
    private val transformValues: FloatBuffer

    init {
        if (!PhysicsLibrary.isPhysicsAvailable()) {
            throw IllegalStateException("Physics library is not available")
        }
        try {
            pointer = PhysicsLibrary.createPhysicsWorld(scene.getPointer(), initialTransform)
        } finally {
            Reference.reachabilityFence(initialTransform)
        }
        transformBuffer = PhysicsLibrary.getTransformBuffer(pointer).order(ByteOrder.nativeOrder())
        transformValues = transformBuffer.asFloatBuffer()
        // transformBuffer.put(initialTransform) // Buffer formats are different now
        // transformBuffer.clear()
        // initialTransform.clear()
    }

    private inline fun <T> requireNotClosed(crossinline block: () -> T): T {
        require(!closed) { "PhysicsWorld is closed" }
        return block()
    }

    fun getTransform(rigidBodyIndex: Int, dst: Matrix4f): Matrix4f = requireNotClosed {
        Objects.checkIndex(rigidBodyIndex, rigidBodyCount)
        val offset = rigidBodyIndex * 28 // 7 floats * 4 bytes
        val px = transformBuffer.getFloat(offset + 0)
        val py = transformBuffer.getFloat(offset + 4)
        val pz = transformBuffer.getFloat(offset + 8)
        val qx = transformBuffer.getFloat(offset + 12)
        val qy = transformBuffer.getFloat(offset + 16)
        val qz = transformBuffer.getFloat(offset + 20)
        val qw = transformBuffer.getFloat(offset + 24)
        dst.translationRotate(px, py, pz, qx, qy, qz, qw)
    }

    fun setTransform(rigidBodyIndex: Int, transform: Matrix4f) {
        Objects.checkIndex(rigidBodyIndex, rigidBodyCount)
        requireNotClosed {
            val offset = rigidBodyIndex * 28 // 7 floats * 4 bytes
            val pos = Vector3f()
            transform.getTranslation(pos)
            val rot = Quaternionf()
            transform.getUnnormalizedRotation(rot)
            
            transformBuffer.putFloat(offset + 0, pos.x)
            transformBuffer.putFloat(offset + 4, pos.y)
            transformBuffer.putFloat(offset + 8, pos.z)
            transformBuffer.putFloat(offset + 12, rot.x)
            transformBuffer.putFloat(offset + 16, rot.y)
            transformBuffer.putFloat(offset + 20, rot.z)
            transformBuffer.putFloat(offset + 24, rot.w)
        }
    }

    fun resetRigidBody(rigidBodyIndex: Int, position: Vector3f, rotation: Quaternionf) {
        Objects.checkIndex(rigidBodyIndex, rigidBodyCount)
        requireNotClosed {
            PhysicsLibrary.resetRigidBody(
                pointer,
                rigidBodyIndex,
                position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z, rotation.w,
            )
        }
    }

    fun pullTransforms(dst: FloatArray) {
        requireNotClosed {
            require(dst.size >= rigidBodyCount * 7)
            transformValues.clear()
            transformValues.get(dst, 0, rigidBodyCount * 7)
        }
    }

    fun pushTransforms(src: FloatArray) {
        requireNotClosed {
            require(src.size >= rigidBodyCount * 7)
            transformValues.clear()
            transformValues.put(src, 0, rigidBodyCount * 7)
        }
    }

    fun step(deltaTime: Float, maxSubSteps: Int, fixedTimeStep: Float) {
        PhysicsLibrary.stepPhysicsWorld(pointer, deltaTime, maxSubSteps, fixedTimeStep)
    }

    override fun close() {
        if (closed) {
            return
        }
        PhysicsLibrary.destroyPhysicsWorld(pointer)
        closed = true
    }
}
