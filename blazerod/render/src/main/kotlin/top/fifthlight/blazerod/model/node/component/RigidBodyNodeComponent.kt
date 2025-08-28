package top.fifthlight.blazerod.model.node.component

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import net.minecraft.util.Colors
import org.joml.Matrix4f
import org.joml.Vector3f
import top.fifthlight.blazerod.model.ModelInstance
import top.fifthlight.blazerod.model.RigidBody
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase
import top.fifthlight.blazerod.model.node.getWorldTransform
import top.fifthlight.blazerod.model.util.toRadian
import top.fifthlight.blazerod.util.*

class RigidBodyNodeComponent(
    val rigidBodyIndex: Int,
    val rigidBodyData: RigidBody,
) : RenderNodeComponent<RigidBodyNodeComponent>() {
    override val type: Type<RigidBodyNodeComponent>
        get() = Type.RigidBody

    private val shape by lazy {
        when (rigidBodyData.shape) {
            RigidBody.ShapeType.SPHERE -> SphereCollisionShape(rigidBodyData.shapeSize.x())
            RigidBody.ShapeType.BOX -> BoxCollisionShape(rigidBodyData.shapeSize.toJme())
            RigidBody.ShapeType.CAPSULE -> CapsuleCollisionShape(
                rigidBodyData.shapeSize.x(),
                rigidBodyData.shapeSize.y()
            )
        }
    }
    private val scaleMustBeUniform = when (rigidBodyData.shape) {
        RigidBody.ShapeType.SPHERE -> true
        RigidBody.ShapeType.BOX -> false
        RigidBody.ShapeType.CAPSULE -> true
    }

    private fun Transform.normalizeScale() = if (scaleMustBeUniform) {
        // Sometimes we have a small error on the scale
        setScale(jmeNodeTransform.scale.x)
    } else {
        this
    }

    private val nodeTransformMatrix = Matrix4f()
    private val jmeNodeTransformMatrix = JmeMatrix4f()
    private val jmeNodeTransform = Transform()

    // node world transform -> rigid body world transform
    private val offsetMatrix = Matrix4f()

    // rigid body world transform -> node world transform
    private val inverseOffsetMatrix = Matrix4f()

    override fun onAttached(instance: ModelInstance, node: RenderNode) {
        instance.physicsData?.let { data ->
            val rigidBody = PhysicsRigidBody(
                shape, when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.FOLLOW_BONE -> 0f
                    else -> rigidBodyData.mass
                }
            )
            val rootNodeTransform = instance.scene.rootNode.absoluteTransform
                ?.matrix?.get(nodeTransformMatrix)
                ?: nodeTransformMatrix.identity()
            instance.getWorldTransform(node)
                .invert(offsetMatrix)
                .mul(rootNodeTransform)
                .translate(rigidBodyData.shapePosition)
                .rotateYXZ(rigidBodyData.shapeRotation)
                .invert(inverseOffsetMatrix)
            val jmeNodeTransformMatrix = rootNodeTransform
                .translate(rigidBodyData.shapePosition)
                .rotateYXZ(rigidBodyData.shapeRotation)
                .get(jmeNodeTransformMatrix)
            jmeNodeTransform.apply {
                fromTransformMatrix(jmeNodeTransformMatrix)
                normalizeScale()
            }

            rigidBody.setPhysicsTransform(jmeNodeTransform)

            rigidBody.collisionGroup = rigidBodyData.collisionGroup
            rigidBody.collideWithGroups = rigidBodyData.collisionMask

            rigidBody.linearDamping = rigidBodyData.moveAttenuation
            rigidBody.angularDamping = rigidBodyData.rotationDamping
            rigidBody.restitution = rigidBodyData.repulsion
            rigidBody.friction = rigidBodyData.frictionForce
            // m_additionalDamping

            rigidBody.setSleepingThresholds(0.01f, 0.01f.toRadian())
            rigidBody.setEnableSleep(false)
            if (rigidBodyData.physicsMode == RigidBody.PhysicsMode.FOLLOW_BONE) {
                rigidBody.isKinematic = true
            }

            data.world.addCollisionObject(rigidBody)
            data.rigidBodies[rigidBodyIndex] = rigidBody
        }
    }

    companion object {
        private val updatePhase = listOf<UpdatePhase.Type>(
            UpdatePhase.Type.PHYSICS_UPDATE_PRE,
            UpdatePhase.Type.PHYSICS_UPDATE_POST,
            UpdatePhase.Type.DEBUG_RENDER,
        )
    }

    override val updatePhases: List<UpdatePhase.Type>
        get() = updatePhase

    private val inverseNodeWorldMatrix = Matrix4f()
    private val translation = Vector3f()
    private val jmeTranslation = JmeVector3f()
    override fun update(
        phase: UpdatePhase,
        node: RenderNode,
        instance: ModelInstance,
    ) {
        val physicsData = instance.physicsData ?: return
        val rigidBody = physicsData.getRigidBody(rigidBodyIndex)
        when (phase) {
            is UpdatePhase.PhysicsUpdatePre -> {
                when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.FOLLOW_BONE -> {
                        val jmeNodeTransformMatrix = instance.getWorldTransform(node)
                            .mul(offsetMatrix, nodeTransformMatrix)
                            .get(jmeNodeTransformMatrix)
                        jmeNodeTransform.apply {
                            fromTransformMatrix(jmeNodeTransformMatrix)
                            normalizeScale()
                        }
                        rigidBody.setPhysicsTransform(jmeNodeTransform)
                    }

                    RigidBody.PhysicsMode.PHYSICS -> {
                        // no-op
                    }

                    RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> {
                        // only apply position
                        instance.getWorldTransform(node)
                            .getTranslation(translation)
                            .get(jmeTranslation)
                        rigidBody.setPhysicsLocation(jmeTranslation)
                    }
                }
            }

            is UpdatePhase.PhysicsUpdatePost -> {
                when (rigidBodyData.physicsMode) {
                    RigidBody.PhysicsMode.FOLLOW_BONE -> {
                        // no-op
                    }

                    RigidBody.PhysicsMode.PHYSICS -> {
                        val nodeTransformMatrix = rigidBody.getTransform(jmeNodeTransform)
                            .toTransformMatrix(jmeNodeTransformMatrix)
                            .get(nodeTransformMatrix)
                            .mul(inverseOffsetMatrix)
                        val inverseNodeWorldMatrix = instance.getWorldTransform(node)
                            .invert(inverseNodeWorldMatrix)
                        val deltaTransformMatrix = nodeTransformMatrix.mulLocal(inverseNodeWorldMatrix)
                        instance.setTransformMatrix(node.nodeIndex, TransformId.PHYSICS) {
                            matrix.mul(deltaTransformMatrix)
                        }
                    }

                    RigidBody.PhysicsMode.PHYSICS_PLUS_BONE -> {
                        // only apply rotation
                    }
                }
            }

            is UpdatePhase.DebugRender -> {
                val consumers = phase.vertexConsumerProvider
                val vertexBuffer = consumers.getBuffer(DEBUG_RENDER_LAYER)

                rigidBody.getTransform(jmeNodeTransform)
                nodeTransformMatrix.set(jmeNodeTransform)

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