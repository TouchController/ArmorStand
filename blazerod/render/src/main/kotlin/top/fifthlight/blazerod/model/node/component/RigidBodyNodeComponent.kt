package top.fifthlight.blazerod.model.node.component

import top.fifthlight.blazerod.model.ModelInstance
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase

class RigidBodyNodeComponent : RenderNodeComponent<RigidBodyNodeComponent>() {
    override val type: Type<RigidBodyNodeComponent>
        get() = Type.RigidBody

    override fun onAttached(instance: ModelInstance) {

    }

    companion object {
        private val updatePhase = listOf<UpdatePhase.Type>(
            UpdatePhase.Type.PHYSICS_UPDATE,
            UpdatePhase.Type.DEBUG_RENDER,
        )
    }

    override val updatePhases: List<UpdatePhase.Type>
        get() = updatePhase

    override fun update(
        phase: UpdatePhase,
        node: RenderNode,
        instance: ModelInstance,
    ) {
        when (phase.type) {
            UpdatePhase.Type.PHYSICS_UPDATE -> {

            }

            UpdatePhase.Type.DEBUG_RENDER -> {

            }

            else -> {}
        }
    }

    override fun onClosed() {}
}