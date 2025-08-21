package top.fifthlight.blazerod.model.node.component

import top.fifthlight.blazerod.model.Mesh
import top.fifthlight.blazerod.model.ModelInstance
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase
import top.fifthlight.blazerod.model.node.getWorldTransform
import top.fifthlight.blazerod.model.resource.RenderPrimitive

class PrimitiveNodeComponent(
    val primitiveIndex: Int,
    val primitive: RenderPrimitive,
    val skinIndex: Int?,
    val morphedPrimitiveIndex: Int?,
    val firstPersonFlag: Mesh.FirstPersonFlag = Mesh.FirstPersonFlag.BOTH,
) : RenderNodeComponent<PrimitiveNodeComponent>() {
    init {
        primitive.increaseReferenceCount()
    }

    override fun onClosed() {
        primitive.decreaseReferenceCount()
    }

    override val type: Type<PrimitiveNodeComponent>
        get() = Type.Primitive

    companion object {
        private val updatePhases = listOf(UpdatePhase.Type.RENDER_DATA_UPDATE)
    }

    override val updatePhases
        get() = Companion.updatePhases

    override fun update(
        phase: UpdatePhase,
        node: RenderNode,
        instance: ModelInstance,
    ) {
        if (phase is UpdatePhase.RenderDataUpdate) {
            if (skinIndex != null) {
                return
            }
            instance.modelData.modelMatricesBuffer.edit {
                setMatrix(primitiveIndex, instance.getWorldTransform(node))
            }
        }
    }
}