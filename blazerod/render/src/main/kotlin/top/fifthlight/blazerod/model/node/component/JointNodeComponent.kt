package top.fifthlight.blazerod.model.node.component

import net.minecraft.util.Colors
import org.joml.Matrix4f
import top.fifthlight.blazerod.model.ModelInstance
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase
import top.fifthlight.blazerod.model.node.getWorldTransform

class JointNodeComponent(
    val skinIndex: Int,
    val jointIndex: Int,
) : RenderNodeComponent<JointNodeComponent>() {
    override fun onClosed() {}

    override val type: Type<JointNodeComponent>
        get() = Type.Joint

    companion object {
        private val updatePhases = listOf(UpdatePhase.Type.RENDER_DATA_UPDATE, UpdatePhase.Type.DEBUG_RENDER)
    }

    override val updatePhases
        get() = Companion.updatePhases

    private val cacheMatrix = Matrix4f()

    override fun update(phase: UpdatePhase, node: RenderNode, instance: ModelInstance) {
        when (phase) {
            is UpdatePhase.RenderDataUpdate -> {
                val cacheMatrix = cacheMatrix
                cacheMatrix.set(instance.getWorldTransform(node))
                val skin = instance.scene.skins[skinIndex]
                val skinBuffer = instance.modelData.skinBuffers[skinIndex]
                val inverseMatrix = skin.inverseBindMatrices?.get(jointIndex)
                skinBuffer.edit {
                    inverseMatrix?.let { cacheMatrix.mul(it) }
                    setMatrix(jointIndex, cacheMatrix)
                }
            }

            is UpdatePhase.DebugRender -> {
                if (node.hasComponentOfType(Type.InfluenceSource)) {
                    return
                }
                val consumers = phase.vertexConsumerProvider
                // TODO: find the real parent joint
                node.parent?.let { parentJoint ->
                    val buffer = consumers.getBuffer(DEBUG_RENDER_LAYER)

                    val parent =
                        phase.viewProjectionMatrix.mul(instance.getWorldTransform(parentJoint), phase.cacheMatrix)
                    buffer.vertex(parent, 0f, 0f, 0f).color(Colors.YELLOW).normal(0f, 1f, 0f)
                    val self = phase.viewProjectionMatrix.mul(instance.getWorldTransform(node), phase.cacheMatrix)
                    buffer.vertex(self, 0f, 0f, 0f).color(Colors.RED).normal(0f, 1f, 0f)
                }
            }

            else -> {}
        }
    }
}