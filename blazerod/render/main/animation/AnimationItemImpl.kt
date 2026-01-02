package top.fifthlight.blazerod.animation

import top.fifthlight.blazerod.api.animation.AnimationItem
import top.fifthlight.blazerod.api.animation.AnimationItemInstance
import top.fifthlight.blazerod.api.animation.AnimationItemPendingValues
import top.fifthlight.blazerod.api.animation.MaskableAnimationItemInstance
import top.fifthlight.blazerod.api.resource.ModelInstance
import top.fifthlight.blazerod.api.resource.RenderScene
import top.fifthlight.blazerod.model.animation.Animation
import top.fifthlight.blazerod.model.animation.AnimationContext
import top.fifthlight.blazerod.model.animation.AnimationState
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.RenderSceneImpl
import top.fifthlight.mergetools.api.ActualConstructor
import top.fifthlight.mergetools.api.ActualImpl
import java.util.concurrent.ConcurrentLinkedDeque

@ActualImpl(AnimationItem::class)
class AnimationItemImpl(
    override val name: String? = null,
    val animation: Animation,
    val channels: List<AnimationChannelItem<*, *, *>>,
) : AnimationItem {
    override val duration
        get() = animation.duration

    fun createState(context: AnimationContext) = animation.createState(context)

    companion object {
        @ActualConstructor
        @JvmStatic
        fun load(scene: RenderScene, animation: Animation) = AnimationLoader.load(scene as RenderSceneImpl, animation)
    }
}

class AnimationItemPendingValuesImpl(animationItem: AnimationItemImpl) : AnimationItemPendingValues {
    @Volatile
    var applied: Boolean = false

    val pendingValues = Array(animationItem.channels.size) { animationItem.channels[it].createPendingValue() }
}

@ActualImpl(AnimationItemInstance::class)
class AnimationItemInstanceImpl(val animationItem: AnimationItemImpl) : AnimationItemInstance, MaskableAnimationItemInstance {
    @ActualConstructor("of")
    constructor(animationItem: AnimationItem) : this(animationItem as AnimationItemImpl)

    private val pendingStack = ConcurrentLinkedDeque<AnimationItemPendingValuesImpl>()

    override fun createState(context: AnimationContext) = animationItem.createState(context)

    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    private inline fun <P : Any> AnimationChannelItem<*, *, P>.updateUnsafe(
        context: AnimationContext,
        state: AnimationState,
        pendingValue: Any,
    ) = update(context, state, pendingValue as P)

    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    private inline fun <P : Any> AnimationChannelItem<*, *, P>.applyUnsafe(
        instance: ModelInstanceImpl,
        pendingValue: Any,
    ) = apply(instance, pendingValue as P)

    override fun update(context: AnimationContext, state: AnimationState) =
        (pendingStack.pollLast() ?: AnimationItemPendingValuesImpl(animationItem)).also {
            it.applied = false
            animationItem.channels.forEachIndexed { index, channel ->
                channel.updateUnsafe(context, state, it.pendingValues[index])
            }
        }

    override fun apply(instance: ModelInstance, pendingValues: AnimationItemPendingValues) {
        val instance = instance as ModelInstanceImpl
        val pendingValues = pendingValues as AnimationItemPendingValuesImpl
        animationItem.channels.forEachIndexed { index, channel ->
            pendingValues.pendingValues[index].let { pendingValue ->
                channel.applyUnsafe(instance, pendingValue)
            }
        }
        if (!pendingValues.applied) {
            pendingValues.applied = true
            pendingStack.addLast(pendingValues)
        }
    }

    override fun applyMasked(
        instance: ModelInstance,
        pendingValues: AnimationItemPendingValues,
        allowedNodeIndices: BooleanArray,
    ) {
        val instance = instance as ModelInstanceImpl
        val pendingValues = pendingValues as AnimationItemPendingValuesImpl

        animationItem.channels.forEachIndexed { index, channel ->
            val targetNodeIndex = channel.targetNodeIndex
            if (targetNodeIndex == null || targetNodeIndex !in allowedNodeIndices.indices || !allowedNodeIndices[targetNodeIndex]) {
                return@forEachIndexed
            }

            pendingValues.pendingValues[index].let { pendingValue ->
                channel.applyUnsafe(instance, pendingValue)
            }
        }
        if (!pendingValues.applied) {
            pendingValues.applied = true
            pendingStack.addLast(pendingValues)
        }
    }

    fun recyclePendingValues(pendingValues: AnimationItemPendingValues) {
        val pendingValues = pendingValues as? AnimationItemPendingValuesImpl ?: return
        if (!pendingValues.applied) {
            pendingValues.applied = true
            pendingStack.addLast(pendingValues)
        }
    }
}
