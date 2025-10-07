package top.fifthlight.blazerod.runtime.node

import net.minecraft.client.render.VertexConsumerProvider
import org.joml.Matrix4f
import org.joml.Matrix4fc
import top.fifthlight.blazerod.util.objectpool.ObjectPool

sealed class UpdatePhase(
    val type: Type,
) {
    enum class Type {
        IK_UPDATE,
        INFLUENCE_TRANSFORM_UPDATE,
        PHYSICS_UPDATE_PRE,
        PHYSICS_UPDATE_POST,
        GLOBAL_TRANSFORM_PROPAGATION,
        RENDER_DATA_UPDATE,
        CAMERA_UPDATE,
        DEBUG_RENDER,
    }

    data object IkUpdate : UpdatePhase(Type.IK_UPDATE)

    data object InfluenceTransformUpdate : UpdatePhase(Type.INFLUENCE_TRANSFORM_UPDATE)

    data object PhysicsUpdatePre : UpdatePhase(Type.PHYSICS_UPDATE_PRE)

    data object PhysicsUpdatePost : UpdatePhase(Type.PHYSICS_UPDATE_POST)

    data object GlobalTransformPropagation : UpdatePhase(Type.GLOBAL_TRANSFORM_PROPAGATION)

    data object RenderDataUpdate : UpdatePhase(Type.RENDER_DATA_UPDATE)

    data object CameraUpdate : UpdatePhase(Type.CAMERA_UPDATE)

    @ConsistentCopyVisibility
    data class DebugRender private constructor(
        val viewProjectionMatrix: Matrix4f = Matrix4f(),
        val cacheMatrix: Matrix4f = Matrix4f(),
        private var _vertexConsumerProvider: VertexConsumerProvider? = null,
    ) : UpdatePhase(
        type = Type.DEBUG_RENDER,
    ), AutoCloseable {
        private var recycled = false

        val vertexConsumerProvider: VertexConsumerProvider
            get() = _vertexConsumerProvider!!

        override fun close() {
            if (recycled) {
                return
            }
            recycled = true
            POOL.release(this)
        }

        companion object {
            private val POOL = ObjectPool<DebugRender>(
                identifier = "update_phase_debug_render",
                create = ::DebugRender,
                onAcquired = {
                    recycled = false
                },
                onReleased = { _vertexConsumerProvider = null },
                onClosed = {},
            )

            fun acquire(
                viewProjectionMatrix: Matrix4fc,
                vertexConsumerProvider: VertexConsumerProvider,
            ) = POOL.acquire().apply {
                this.viewProjectionMatrix.set(viewProjectionMatrix)
                _vertexConsumerProvider = vertexConsumerProvider
            }
        }
    }
}
