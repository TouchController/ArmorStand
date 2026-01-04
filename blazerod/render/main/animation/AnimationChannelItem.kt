package top.fifthlight.blazerod.animation

import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.blazerod.api.resource.RenderExpression
import top.fifthlight.blazerod.api.resource.RenderExpressionGroup
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.model.animation.AnimationChannel
import top.fifthlight.blazerod.model.animation.AnimationContext
import top.fifthlight.blazerod.model.animation.AnimationState
import top.fifthlight.blazerod.model.util.MutableFloat
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.resource.CameraTransformImpl

sealed class AnimationChannelItem<T : Any, D, P : Any>(
    val channel: AnimationChannel<T, D>,
) {
    open val targetNodeIndex: Int? = null

    @Suppress("UNCHECKED_CAST")
    fun applyUnsafe(instance: ModelInstanceImpl, pendingValue: Any) {
        (this as AnimationChannelItem<Any, Any, Any>).apply(instance, pendingValue)
    }

    abstract fun createPendingValue(): P

    // run on client thread
    abstract fun update(context: AnimationContext, state: AnimationState, pendingValue: P)

    // run on render thread
    abstract fun apply(instance: ModelInstanceImpl, pendingValue: P)

    class TranslationItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Vector3f, Unit>,
    ) : AnimationChannelItem<Vector3f, Unit, Vector3f>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.Translation) { "Unmatched animation channel: want translation, but got ${channel.type}" }
        }

        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            instance.setTransformDecomposed(index, transformId) {
                translation.set(pendingValue)
            }
        }
    }

    class ScaleItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Vector3f, Unit>,
    ) : AnimationChannelItem<Vector3f, Unit, Vector3f>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.Scale) { "Unmatched animation channel: want scale, but got ${channel.type}" }
        }

        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            instance.setTransformDecomposed(index, transformId) {
                scale.set(pendingValue)
            }
        }
    }

    class RotationItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Quaternionf, Unit>,
    ) : AnimationChannelItem<Quaternionf, Unit, Quaternionf>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.Rotation) { "Unmatched animation channel: want rotation, but got ${channel.type}" }
        }

        override fun createPendingValue() = Quaternionf()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Quaternionf) {
            channel.getData(context, state, pendingValue)
            pendingValue.normalize()
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Quaternionf) {
            instance.setTransformDecomposed(index, transformId) {
                rotation.set(pendingValue)
            }
        }
    }

    class BedrockTranslationItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Vector3f, Unit>,
    ) : AnimationChannelItem<Vector3f, Unit, Vector3f>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.BedrockTranslation) { "Unmatched animation channel: want translation, but got ${channel.type}" }
        }

        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            instance.setTransformBedrock(index, transformId) {
                translation.set(pendingValue)
            }
        }
    }

    class BedrockScaleItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Vector3f, Unit>,
    ) : AnimationChannelItem<Vector3f, Unit, Vector3f>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.BedrockScale) { "Unmatched animation channel: want scale, but got ${channel.type}" }
        }

        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            instance.setTransformBedrock(index, transformId) {
                scale.set(pendingValue)
            }
        }
    }

    class BedrockRotationItem(
        private val index: Int,
        private val transformId: TransformId,
        channel: AnimationChannel<Quaternionf, Unit>,
    ) : AnimationChannelItem<Quaternionf, Unit, Quaternionf>(channel) {
        override val targetNodeIndex: Int = index

        init {
            require(channel.type == AnimationChannel.Type.BedrockRotation) { "Unmatched animation channel: want rotation, but got ${channel.type}" }
        }

        override fun createPendingValue() = Quaternionf()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Quaternionf) {
            channel.getData(context, state, pendingValue)
            pendingValue.normalize()
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Quaternionf) {
            instance.setTransformBedrock(index, transformId) {
                rotation.set(pendingValue)
            }
        }
    }

    class MorphItem(
        private val primitiveIndex: Int,
        private val targetGroupIndex: Int,
        channel: AnimationChannel<MutableFloat, AnimationChannel.Type.MorphData>,
    ) : AnimationChannelItem<MutableFloat, AnimationChannel.Type.MorphData, MutableFloat>(channel) {
        override fun createPendingValue() = MutableFloat()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: MutableFloat) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: MutableFloat) {
            instance.setGroupWeight(primitiveIndex, targetGroupIndex, pendingValue.value)
        }
    }

    protected fun RenderExpression.apply(instance: ModelInstanceImpl, weight: Float) = bindings.forEach { binding ->
        when (binding) {
            is RenderExpression.Binding.MorphTarget -> {
                instance.setGroupWeight(binding.morphedPrimitiveIndex, binding.groupIndex, weight)
            }
        }
    }

    class ExpressionItem(
        val expression: RenderExpression,
        channel: AnimationChannel<MutableFloat, AnimationChannel.Type.ExpressionData>,
    ) : AnimationChannelItem<MutableFloat, AnimationChannel.Type.ExpressionData, MutableFloat>(channel) {
        override fun createPendingValue() = MutableFloat()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: MutableFloat) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: MutableFloat) {
            expression.apply(instance, pendingValue.value)
        }
    }

    class ExpressionGroupItem(
        val group: RenderExpressionGroup,
        channel: AnimationChannel<MutableFloat, AnimationChannel.Type.ExpressionData>,
    ) : AnimationChannelItem<MutableFloat, AnimationChannel.Type.ExpressionData, MutableFloat>(channel) {
        override fun createPendingValue() = MutableFloat()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: MutableFloat) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: MutableFloat) {
            for (item in group.items) {
                val expression = instance.scene.expressions[item.expressionIndex]
                expression.apply(instance, pendingValue.value * item.influence)
            }
        }
    }

    class CameraFovItem(
        val cameraIndex: Int,
        channel: AnimationChannel<MutableFloat, AnimationChannel.Type.CameraData>,
    ) : AnimationChannelItem<MutableFloat, AnimationChannel.Type.CameraData, MutableFloat>(channel) {
        override fun createPendingValue() = MutableFloat()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: MutableFloat) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: MutableFloat) {
            when (val camera = instance.modelData.cameraTransforms[cameraIndex]) {
                is CameraTransformImpl.MMD -> camera.fov = pendingValue.value
                is CameraTransformImpl.Perspective -> camera.yfov = pendingValue.value
                else -> Unit
            }
        }
    }

    class MMDCameraDistanceItem(
        val cameraIndex: Int,
        channel: AnimationChannel<MutableFloat, AnimationChannel.Type.CameraData>,
    ) : AnimationChannelItem<MutableFloat, AnimationChannel.Type.CameraData, MutableFloat>(channel) {
        override fun createPendingValue() = MutableFloat()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: MutableFloat) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: MutableFloat) {
            val camera = instance.modelData.cameraTransforms[cameraIndex] as? CameraTransformImpl.MMD ?: return
            camera.distance = pendingValue.value
        }
    }

    class MMDCameraTargetItem(
        val cameraIndex: Int,
        channel: AnimationChannel<Vector3f, AnimationChannel.Type.CameraData>,
    ) : AnimationChannelItem<Vector3f, AnimationChannel.Type.CameraData, Vector3f>(channel) {
        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            val camera = instance.modelData.cameraTransforms[cameraIndex] as? CameraTransformImpl.MMD ?: return
            camera.targetPosition.set(pendingValue)
        }
    }

    class MMDCameraRotationItem(
        val cameraIndex: Int,
        channel: AnimationChannel<Vector3f, AnimationChannel.Type.CameraData>,
    ) : AnimationChannelItem<Vector3f, AnimationChannel.Type.CameraData, Vector3f>(channel) {
        override fun createPendingValue() = Vector3f()

        override fun update(context: AnimationContext, state: AnimationState, pendingValue: Vector3f) {
            channel.getData(context, state, pendingValue)
        }

        override fun apply(instance: ModelInstanceImpl, pendingValue: Vector3f) {
            val camera = instance.modelData.cameraTransforms[cameraIndex] as? CameraTransformImpl.MMD ?: return
            camera.rotationEulerAngles.set(pendingValue)
        }
    }
}
