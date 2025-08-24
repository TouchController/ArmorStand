package top.fifthlight.armorstand.state

import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.extension.internal.PlayerEntityRenderStateExtInternal
import top.fifthlight.armorstand.ui.model.AnimationViewModel
import top.fifthlight.armorstand.util.toRadian
import top.fifthlight.armorstand.vmc.VmcMarionetteManager
import top.fifthlight.blazerod.animation.AnimationItem
import top.fifthlight.blazerod.animation.Timeline
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.resource.RenderExpression
import top.fifthlight.blazerod.model.resource.RenderExpressionGroup
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

sealed class ModelController {
    open fun update(uuid: UUID, vanillaState: PlayerEntityRenderState) = Unit
    abstract fun apply(instance: ModelInstance)

    private class JointItem(
        private val nodeIndex: Int,
    ) {
        fun update(instance: ModelInstance, func: NodeTransform.Decomposed.() -> Unit) {
            instance.setTransformDecomposed(nodeIndex, TransformId.RELATIVE_ANIMATION, func)
        }

        fun updateAbsolute(instance: ModelInstance, func: NodeTransform.Decomposed.() -> Unit) {
            instance.setTransformDecomposed(nodeIndex, TransformId.ABSOLUTE, func)
        }
    }

    private sealed class ExpressionItem {
        abstract fun apply(instance: ModelInstance, weight: Float)

        fun RenderExpression.apply(instance: ModelInstance, weight: Float) = bindings.forEach { binding ->
            when (binding) {
                is RenderExpression.Binding.MorphTarget -> {
                    instance.setGroupWeight(binding.morphedPrimitiveIndex, binding.groupIndex, weight)
                }
            }
        }

        data class Expression(
            val expression: RenderExpression,
        ) : ExpressionItem() {
            override fun apply(instance: ModelInstance, weight: Float) = expression.apply(instance, weight)
        }

        data class Group(
            val group: RenderExpressionGroup,
        ) : ExpressionItem() {
            override fun apply(instance: ModelInstance, weight: Float) = group.items.forEach { item ->
                val expression = instance.scene.expressions[item.expressionIndex]
                expression.apply(instance, weight * item.influence)
            }
        }
    }

    companion object {
        private fun RenderScene.getBone(tag: HumanoidTag) =
            humanoidTagMap[tag]?.let { node -> JointItem(nodeIndex = node.nodeIndex) }

        private fun RenderScene.getExpression(tag: Expression.Tag) =
            expressions.firstOrNull { it.tag == tag }?.let { ExpressionItem.Expression(it) }
                ?: expressionGroups.firstOrNull { it.tag == tag }?.let { ExpressionItem.Group(it) }

        private const val NANOSECONDS_PER_SECOND = 1_000_000_000L

        private val invertHeadDirection: Boolean
            get() = ConfigHolder.config.value.invertHeadDirection

        fun calculateBlinkProgress(
            playerUuid: UUID,
            averageBlinkInterval: Long,
            blinkDuration: Long,
            currentTime: Long,
        ): Float {
            val seed1 = playerUuid.mostSignificantBits
            val seed2 = playerUuid.leastSignificantBits
            val seed = seed1 xor seed2

            val offsetMillis = (seed % (averageBlinkInterval * 2)).coerceAtLeast(0)
            val effectiveTime = currentTime + offsetMillis
            val cycleProgress = effectiveTime % averageBlinkInterval
            return if (cycleProgress < blinkDuration) {
                val phase = (cycleProgress.toFloat() / blinkDuration.toFloat()) * MathHelper.PI
                sin(phase)
            } else {
                0f
            }
        }

        fun calculateBlinkProgress(
            playerUuid: UUID,
            averageBlinkInterval: Double,
            blinkDuration: Double,
            currentTime: Long,
        ) = calculateBlinkProgress(
            playerUuid,
            (averageBlinkInterval * NANOSECONDS_PER_SECOND).toLong(),
            (blinkDuration * NANOSECONDS_PER_SECOND).toLong(),
            currentTime,
        )
    }

    class LiveUpdated private constructor(
        private val center: JointItem?,
        private val head: JointItem?,
        private val blinkExpression: ExpressionItem?,
    ) : ModelController() {
        private var bodyYaw: Float = 0f
        private var headYaw: Float = 0f
        private var headPitch: Float = 0f
        private var blinkProgress: Float = 0f

        constructor(scene: RenderScene) : this(
            center = scene.getBone(HumanoidTag.HIPS),
            head = scene.getBone(HumanoidTag.HEAD),
            blinkExpression = scene.getExpression(Expression.Tag.BLINK),
        )

        override fun update(uuid: UUID, vanillaState: PlayerEntityRenderState) {
            bodyYaw = MathHelper.PI - vanillaState.bodyYaw.toRadian()
            headYaw = -vanillaState.relativeHeadYaw.toRadian()
            headPitch = if (invertHeadDirection) {
                vanillaState.pitch.toRadian()
            } else {
                -vanillaState.pitch.toRadian()
            }
            blinkProgress = calculateBlinkProgress(
                playerUuid = uuid,
                averageBlinkInterval = 4.0,
                blinkDuration = 0.25,
                currentTime = System.nanoTime(),
            )
        }

        override fun apply(instance: ModelInstance) {
            center?.update(instance) {
                rotation.rotationY(bodyYaw)
            }
            head?.update(instance) {
                rotation.rotationYXZ(headYaw, headPitch, 0f)
            }
            blinkExpression?.apply(instance, blinkProgress)
        }
    }

    class Predefined(
        private val animation: AnimationItem,
    ) : ModelController() {
        val timeline: Timeline = Timeline(
            duration = animation.duration.toDouble(),
            speed = AnimationViewModel.playSpeed.value,
            loop = true,
        ).also { timeline ->
            timeline.play(System.nanoTime())
        }

        override fun apply(instance: ModelInstance) {
            val time = timeline.getCurrentTime(System.nanoTime())
            animation.apply(instance, time.toFloat())
        }
    }

    class LiveSwitched private constructor(
        private val animationSet: FullAnimationSet,
        private val head: JointItem?,
        private val blinkExpression: ExpressionItem?,
    ) : ModelController() {
        constructor(
            scene: RenderScene,
            animationSet: FullAnimationSet,
        ) : this(
            animationSet = animationSet,
            head = scene.getBone(HumanoidTag.HEAD),
            blinkExpression = scene.getExpression(Expression.Tag.BLINK),
        )

        sealed class State {
            abstract fun getItem(set: FullAnimationSet): AnimationItem
            open val loop: Boolean = true

            data object Idle : State() {
                override fun getItem(set: FullAnimationSet) = set.idle
            }

            data object Walking : State() {
                override fun getItem(set: FullAnimationSet) = set.walk
            }

            data object ElytraFly : State() {
                override fun getItem(set: FullAnimationSet) = set.elytraFly
            }

            data object Swimming : State() {
                override fun getItem(set: FullAnimationSet) = set.swim
            }

            data object Sleeping : State() {
                override fun getItem(set: FullAnimationSet) = set.sleep
            }

            data object Riding : State() {
                override fun getItem(set: FullAnimationSet) = set.ride
            }

            data object OnHorse : State() {
                override fun getItem(set: FullAnimationSet) = set.onHorse
            }

            data object Dying : State() {
                override fun getItem(set: FullAnimationSet) = set.die
                override val loop: Boolean
                    get() = false
            }

            data object Sprinting : State() {
                override fun getItem(set: FullAnimationSet) = set.sprint
            }

            data object Sneaking : State() {
                override fun getItem(set: FullAnimationSet) = set.sneak
            }
        }

        private var state: State = State.Idle
        private var item: AnimationItem? = null
        private var timeline: Timeline? = null
        private var reset = false
        private var bodyYaw: Float = 0f
        private var headYaw: Float = 0f
        private var headPitch: Float = 0f
        private var blinkProgress: Float = 0f

        companion object {
            private val PlayerEntityRenderState.vehicleType
                get() = (this as PlayerEntityRenderStateExtInternal).`armorstand$getRidingEntityType`()
            private val PlayerEntityRenderState.isSprinting
                get() = (this as PlayerEntityRenderStateExtInternal).`armorstand$isSprinting`()
            private val PlayerEntityRenderState.isDead
                get() = (this as PlayerEntityRenderStateExtInternal).`armorstand$isDead`()
            private val PlayerEntityRenderState.limbSwingSpeed
                get() = (this as PlayerEntityRenderStateExtInternal).`armorstand$getLimbSwingSpeed`()
            private val horseEntityTypes = listOf(
                EntityType.HORSE,
                EntityType.DONKEY,
                EntityType.MULE,
                EntityType.LLAMA,
                EntityType.SKELETON_HORSE,
                EntityType.ZOMBIE_HORSE,
            )
        }

        private fun getState(vanillaState: PlayerEntityRenderState): State = when {
            vanillaState.vehicleType in horseEntityTypes -> State.OnHorse
            vanillaState.vehicleType != null -> State.Riding
            vanillaState.isDead -> State.Dying
            vanillaState.pose == EntityPose.CROUCHING -> State.Sneaking
            vanillaState.pose == EntityPose.GLIDING -> State.ElytraFly
            vanillaState.pose == EntityPose.SLEEPING -> State.Sleeping
            vanillaState.pose == EntityPose.SWIMMING -> State.Swimming

            else -> if (vanillaState.isSprinting) {
                State.Sprinting
            } else if (vanillaState.limbSwingSpeed > .4f) {
                State.Walking
            } else {
                State.Idle
            }
        }

        override fun update(uuid: UUID, vanillaState: PlayerEntityRenderState) {
            val sleepingDirection = vanillaState.sleepingDirection
            bodyYaw = if (vanillaState.isInPose(EntityPose.SLEEPING) && sleepingDirection != null) {
                when (sleepingDirection) {
                    Direction.SOUTH -> 0f
                    Direction.EAST -> PI.toFloat() * 0.5f
                    Direction.NORTH -> PI.toFloat()
                    Direction.WEST -> PI.toFloat() * 1.5f
                    else -> 0f
                }
            } else {
                MathHelper.PI - vanillaState.bodyYaw.toRadian()
            }
            headYaw = -vanillaState.relativeHeadYaw.toRadian()
            headPitch = if (invertHeadDirection) {
                vanillaState.pitch.toRadian()
            } else {
                -vanillaState.pitch.toRadian()
            }
            blinkProgress = calculateBlinkProgress(
                playerUuid = uuid,
                averageBlinkInterval = 4.0,
                blinkDuration = 0.25,
                currentTime = System.nanoTime(),
            )
            val newState = getState(vanillaState)
            if (newState != state) {
                this.state = newState
            }
            val newItem = newState.getItem(animationSet)
            if (newItem != item || timeline == null) {
                timeline = Timeline(
                    duration = newItem.duration.toDouble(),
                    speed = AnimationViewModel.playSpeed.value,
                    loop = true,
                ).apply {
                    setLoop(newState.loop)
                    play(System.nanoTime())
                }
                item = newItem
                reset = true
            }
        }

        override fun apply(instance: ModelInstance) {
            val timeline = timeline ?: return
            val item = item ?: return
            if (reset) {
                instance.clearTransform()
                reset = false
            }
            val time = timeline.getCurrentTime(System.nanoTime())
            item.apply(instance, time.toFloat())
            instance.setTransformDecomposed(instance.scene.rootNode.nodeIndex, TransformId.RELATIVE_ANIMATION) {
                rotation.rotationY(bodyYaw)
            }
            head?.update(instance) {
                rotation.rotationYXZ(headYaw, headPitch, 0f)
            }
            blinkExpression?.apply(instance, blinkProgress)
        }
    }

    class Vmc(
        private val scene: RenderScene,
    ) : ModelController() {
        private val bones = mutableMapOf<HumanoidTag, Optional<JointItem>>()
        private val expressions = mutableMapOf<Expression.Tag, Optional<ExpressionItem>>()

        override fun apply(instance: ModelInstance) {
            val state = VmcMarionetteManager.getState() ?: return
            state.rootTransform?.let {
                instance.setTransformDecomposed(scene.rootNode.nodeIndex, TransformId.ABSOLUTE) {
                    translation.set(it.position)
                    rotation.set(it.rotation)
                }
            }
            state.boneTransforms.forEach { (bone, value) ->
                val item = bones.getOrPut(bone) { Optional.ofNullable(scene.getBone(bone)) }
                item.ifPresent {
                    it.updateAbsolute(instance) {
                        translation.set(value.position)
                        rotation.set(value.rotation)
                    }
                }
            }
            state.blendShapes.forEach { (tag, value) ->
                val item = expressions.getOrPut(tag) { Optional.ofNullable(scene.getExpression(tag)) }
                item.ifPresent {
                    it.apply(instance, value)
                }
            }
        }
    }
}
