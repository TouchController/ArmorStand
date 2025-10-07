package top.fifthlight.blazerod.runtime.node.component

import top.fifthlight.blazerod.model.RigidBody
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase

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

    override fun update(
        phase: UpdatePhase,
        node: RenderNodeImpl,
        instance: ModelInstanceImpl,
    ) {

    }

    override fun onClosed() {}
}