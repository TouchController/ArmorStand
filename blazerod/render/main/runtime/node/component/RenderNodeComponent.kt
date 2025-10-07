package top.fifthlight.blazerod.runtime.node.component

import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import top.fifthlight.blazerod.api.refcount.AbstractRefCount
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import java.util.*

sealed class RenderNodeComponent<C : RenderNodeComponent<C>> : AbstractRefCount() {
    companion object {
        protected val DEBUG_RENDER_LAYER: RenderLayer.MultiPhase = RenderLayer.of(
            "blazerod_joint_debug_lines",
            1536,
            RenderPipelines.LINES,
            RenderLayer.MultiPhaseParameters.builder()
                .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(1.0)))
                .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                .target(RenderPhase.ITEM_ENTITY_TARGET)
                .build(false)
        )
    }

    override val typeId: String
        get() = "node"

    sealed class Type<C : RenderNodeComponent<C>> {
        object Primitive : Type<PrimitiveComponent>()
        object Joint : Type<JointComponent>()
        object InfluenceSource : Type<InfluenceSourceComponent>()
        object Camera : Type<CameraComponent>()
        object IkTarget : Type<IkTargetComponent>()
        object RigidBody : Type<RigidBodyComponent>()
    }

    abstract val type: Type<C>

    open fun onAttached(instance: ModelInstanceImpl, node: RenderNodeImpl) {}

    abstract val updatePhases: List<UpdatePhase.Type>
    abstract fun update(phase: UpdatePhase, node: RenderNodeImpl, instance: ModelInstanceImpl)

    lateinit var node: RenderNodeImpl
        internal set
}

