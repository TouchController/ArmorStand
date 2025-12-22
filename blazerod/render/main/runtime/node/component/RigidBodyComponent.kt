package top.fifthlight.blazerod.runtime.node.component

import net.minecraft.util.Colors
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.blazerod.model.RigidBody
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import top.fifthlight.blazerod.runtime.node.getTransformMap
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
    private val baseWorldMatrix = Matrix4f()
    private val parentWorldMatrix = Matrix4f()

    private val tempPos = Vector3f()
    private val tempRot = Quaternionf()

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
                        nodeTransformMatrix.getTranslation(tempPos)
                        nodeTransformMatrix.getUnnormalizedRotation(tempRot)
                        
                        val offset = rigidBodyIndex * 7
                        val array = physicsData.transformArray
                        array[offset + 0] = tempPos.x
                        array[offset + 1] = tempPos.y
                        array[offset + 2] = tempPos.z
                        array[offset + 3] = tempRot.x
                        array[offset + 4] = tempRot.y
                        array[offset + 5] = tempRot.z
                        array[offset + 6] = tempRot.w
                    }

                    RigidBody.PhysicsMode.PHYSICS -> {
                        // no-op
                    }
                }
            }

            is UpdatePhase.PhysicsUpdatePost -> {
                when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.PHYSICS, RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> {
                        val offset = rigidBodyIndex * 7

                        val array = physicsData.transformArray

                        val px = array[offset + 0]
                        val py = array[offset + 1]
                        val pz = array[offset + 2]
                        val qx = array[offset + 3]
                        val qy = array[offset + 4]
                        val qz = array[offset + 5]
                        val qw = array[offset + 6]
                        physicsMatrix.translationRotate(px, py, pz, qx, qy, qz, qw)

                        val localBase = instance.getTransformMap(node).getSum(TransformId.EXTERNAL_PARENT_DEFORM)
                        baseWorldMatrix.set(localBase)
                        val parent = node.parent
                        if (parent != null) {
                            val parentRigidBody = parent
                                .getComponentsOfType(RenderNodeComponent.Type.RigidBody)
                                .firstOrNull()
                            if (parentRigidBody != null) {
                                val parentOffset = parentRigidBody.rigidBodyIndex * 7
                                parentWorldMatrix.translationRotate(
                                    physicsData.transformArray[parentOffset + 0],
                                    physicsData.transformArray[parentOffset + 1],
                                    physicsData.transformArray[parentOffset + 2],
                                    physicsData.transformArray[parentOffset + 3],
                                    physicsData.transformArray[parentOffset + 4],
                                    physicsData.transformArray[parentOffset + 5],
                                    physicsData.transformArray[parentOffset + 6],
                                )
                            } else {
                                parentWorldMatrix.set(instance.getWorldTransform(parent))
                            }
                            parentWorldMatrix.mul(baseWorldMatrix, baseWorldMatrix)
                        }
                        if (rigidBodyData.physicsMode == RigidBody.PhysicsMode.PHYSICS_PLUS_BONE) {
                            baseWorldMatrix.getTranslation(tempPos)
                            physicsMatrix.setTranslation(tempPos)
                        }

                        baseWorldMatrix.invert(inverseNodeWorldMatrix)
                        inverseNodeWorldMatrix.mul(physicsMatrix)

                        instance.setTransformMatrix(node.nodeIndex, TransformId.PHYSICS) {
                            this.matrix.set(inverseNodeWorldMatrix)
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