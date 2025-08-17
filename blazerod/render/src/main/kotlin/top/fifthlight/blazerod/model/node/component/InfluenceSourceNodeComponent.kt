package top.fifthlight.blazerod.model.node.component

import org.joml.Quaternionf
import org.joml.Quaternionfc
import top.fifthlight.blazerod.model.ModelInstance
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase

class InfluenceSourceNodeComponent(
    val target: TransformId,
    val targetNodeIndex: Int,
    val influence: Float,
    val influenceRotation: Boolean = false,
    val influenceTranslation: Boolean = false,
    val appendLocal: Boolean = false,
) : RenderNodeComponent<InfluenceSourceNodeComponent>() {
    override fun onClosed() {}

    override val type: Type<InfluenceSourceNodeComponent>
        get() = Type.InfluenceSource

    override fun onAttached(instance: ModelInstance) {}

    companion object {
        private val updatePhases =
            listOf(UpdatePhase.Type.INFLUENCE_TRANSFORM_UPDATE)

        private val identity: Quaternionfc = Quaternionf()
    }

    override val updatePhases
        get() = Companion.updatePhases

    private val sourceIkRotation = Quaternionf()
    override fun update(phase: UpdatePhase, node: RenderNode, instance: ModelInstance) {
        if (phase is UpdatePhase.InfluenceTransformUpdate) {
            val sourceTransformMap = instance.modelData.transformMaps[node.nodeIndex]
            instance.setTransformDecomposed(targetNodeIndex, target) {
                if (influenceRotation) {
                    val nestedAppend = sourceTransformMap.get(target)
                    if (appendLocal || nestedAppend == null) {
                        sourceTransformMap.get(TransformId.RELATIVE_ANIMATION)?.getRotation(rotation)
                            ?: rotation.identity()
                    } else {
                        nestedAppend.getRotation(rotation)
                    }
                    val sourceIk = sourceTransformMap.get(TransformId.IK)
                    if (sourceIk != null) {
                        sourceIk.getRotation(sourceIkRotation)
                        rotation.mul(sourceIkRotation)
                    }
                    identity.slerp(rotation, influence, rotation)
                }
                if (influenceTranslation) {
                    val nestedAppend = sourceTransformMap.get(target)
                    if (appendLocal || nestedAppend == null) {
                        sourceTransformMap.get(TransformId.RELATIVE_ANIMATION)?.getTranslation(translation)
                            ?: translation.set(0f)
                    } else {
                        nestedAppend.getTranslation(translation)
                    }
                    translation.mul(influence)
                }
            }
        }
    }
}