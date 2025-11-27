package top.fifthlight.blazerod.runtime.node.component

import net.minecraft.util.Colors
import org.joml.Matrix4f
import org.joml.Vector3f
import top.fifthlight.blazerod.model.RigidBody
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import top.fifthlight.blazerod.runtime.node.getWorldTransform

class RigidBodyComponent(
    val rigidBodyIndex: Int,
    val rigidBodyData: RigidBody,
) : RenderNodeComponent<RigidBodyComponent>() {
    override val type: Type<RigidBodyComponent>
        get() = Type.RigidBody

    companion object {
        private val updatePhase = listOf<UpdatePhase.Type>(
            UpdatePhase.Type.PHYSICS_UPDATE_PRE,
            UpdatePhase.Type.PHYSICS_UPDATE_POST,
            UpdatePhase.Type.DEBUG_RENDER,
        )
    }

    override val updatePhases: List<UpdatePhase.Type>
        get() = updatePhase

    private val physicsMatrix = Matrix4f()
    private val inverseNodeWorldMatrix = Matrix4f()
    override fun update(
        phase: UpdatePhase,
        node: RenderNodeImpl,
        instance: ModelInstanceImpl,
    ) {
        val physicsData = instance.physicsData ?: return
        when (phase) {
            is UpdatePhase.PhysicsUpdatePre -> {
                when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.FOLLOW_BONE, RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> {
                        val nodeTransformMatrix = instance.getWorldTransform(node)
                        physicsData.world.setTransform(rigidBodyIndex, nodeTransformMatrix)
                    }

                    RigidBody.PhysicsMode.PHYSICS -> {
                        // no-op
                    }
                }
            }

            is UpdatePhase.PhysicsUpdatePost -> {
                when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.PHYSICS, RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> {
                        val physicsMatrix = physicsData.world.getTransform(rigidBodyIndex, physicsMatrix)
                        val inverseNodeWorldMatrix = instance.getWorldTransform(node).invert(inverseNodeWorldMatrix)
                        val deltaTransformMatrix = physicsMatrix.mul(inverseNodeWorldMatrix)
                        instance.setTransformMatrix(node.nodeIndex, TransformId.PHYSICS) {
                            matrix.mul(deltaTransformMatrix)
                        }
                    }

                    RigidBody.PhysicsMode.FOLLOW_BONE -> {
                        // no-op
                    }
                }
            }

            is UpdatePhase.DebugRender -> {
                val consumers = phase.vertexConsumerProvider
                val vertexBuffer = consumers.getBuffer(DEBUG_RENDER_LAYER)

                val nodeTransformMatrix = instance.getWorldTransform(node)
                val matrix = phase.viewProjectionMatrix.mul(nodeTransformMatrix, phase.cacheMatrix)

                val color = when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.FOLLOW_BONE -> Colors.PURPLE
                    RigidBody.PhysicsMode.PHYSICS -> Colors.RED
                    RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> Colors.GREEN
                }

                when (rigidBodyData.shape) {
                    RigidBody.ShapeType.SPHERE -> {
                        vertexBuffer.drawSphereWireframe(
                            matrix = matrix,
                            radius = rigidBodyData.shapeSize.x(),
                            segments = 16,
                            color = color,
                        )
                    }

                    RigidBody.ShapeType.BOX -> {
                        vertexBuffer.drawBoxWireframe(
                            matrix = matrix,
                            width = rigidBodyData.shapeSize.x(),
                            height = rigidBodyData.shapeSize.y(),
                            length = rigidBodyData.shapeSize.z(),
                            color = color,
                        )
                    }

                    RigidBody.ShapeType.CAPSULE -> {
                        vertexBuffer.drawCapsuleWireframe(
                            matrix = matrix,
                            radius = rigidBodyData.shapeSize.x(),
                            height = rigidBodyData.shapeSize.y(),
                            segments = 16,
                            color = color,
                        )
                    }
                }
            }

            else -> {}
        }
    }

    override fun onClosed() {}
}