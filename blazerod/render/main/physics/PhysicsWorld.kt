package top.fifthlight.blazerod.physics

import org.joml.Matrix4f
import java.lang.AutoCloseable
import java.lang.ref.Reference
import java.nio.ByteBuffer

class PhysicsWorld(
    scene: PhysicsScene,
    initialTransform: ByteBuffer,
) : AutoCloseable {
    private val pointer: Long
    private var closed = false
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
        transformBuffer = PhysicsLibrary.getTransformBuffer(pointer)
    }

    private inline fun <T> requireNotClosed(crossinline block: () -> T): T {
        require(!closed) { "PhysicsWorld is closed" }
        return block()
    }

    fun getTransform(rigidBodyIndex: Int, dst: Matrix4f): Matrix4f = requireNotClosed {
        dst.apply {
            dst.set(rigidBodyIndex * 64, transformBuffer)
        }
    }

    fun setTransform(rigidBodyIndex: Int, transform: Matrix4f) {
        requireNotClosed {
            transform.get(rigidBodyIndex * 64, transformBuffer)
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        PhysicsLibrary.destroyPhysicsWorld(pointer)
        closed = true
    }
}
