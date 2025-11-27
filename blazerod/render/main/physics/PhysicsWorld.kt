package top.fifthlight.blazerod.physics

import org.joml.Matrix4f
import java.lang.AutoCloseable
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class PhysicsWorld(
    scene: PhysicsScene,
    initialTransform: ByteBuffer,
) : AutoCloseable {
    private val pointer: Long
    private var closed = false
    internal val rigidBodyCount = scene.rigidBodyCount
    private val transformBuffer: ByteBuffer

    init {
        if (!PhysicsLibrary.isPhysicsAvailable()) {
            throw IllegalStateException("Physics library is not available")
        }
        try {
            pointer = PhysicsLibrary.createPhysicsWorld(scene.getPointer(), initialTransform)
        } finally {
            Reference.reachabilityFence(initialTransform)
        }
        transformBuffer = ByteBuffer.allocateDirect(initialTransform.capacity()).order(ByteOrder.nativeOrder())//PhysicsLibrary.getTransformBuffer(pointer).order(ByteOrder.nativeOrder())
        transformBuffer.put(initialTransform)
        transformBuffer.clear()
        initialTransform.clear()
    }

    private inline fun <T> requireNotClosed(crossinline block: () -> T): T {
        require(!closed) { "PhysicsWorld is closed" }
        return block()
    }

    fun getTransform(rigidBodyIndex: Int, dst: Matrix4f): Matrix4f = requireNotClosed {
        Objects.checkIndex(rigidBodyIndex, rigidBodyCount)
        dst.apply {
            set(rigidBodyIndex * 64, transformBuffer)
        }
    }

    fun setTransform(rigidBodyIndex: Int, transform: Matrix4f) {
        Objects.checkIndex(rigidBodyIndex, rigidBodyCount)
        requireNotClosed {
            transform.get(rigidBodyIndex * 64, transformBuffer)
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
