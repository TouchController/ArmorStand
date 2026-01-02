package top.fifthlight.armorstand.state

import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.registry.tag.EntityTypeTags
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.Arm
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import top.fifthlight.armorstand.extension.internal.PlayerEntityRenderStateExtInternal
import top.fifthlight.armorstand.util.toRadian
import top.fifthlight.armorstand.vmc.VmcMarionetteManager
import top.fifthlight.blazerod.animation.AnimationItemInstanceImpl
import top.fifthlight.blazerod.api.animation.AnimationContextsFactory
import top.fifthlight.blazerod.api.animation.AnimationItemInstance
import top.fifthlight.blazerod.api.animation.AnimationItemPendingValues
import top.fifthlight.blazerod.api.resource.ModelInstance
import top.fifthlight.blazerod.api.resource.RenderExpression
import top.fifthlight.blazerod.api.resource.RenderExpressionGroup
import top.fifthlight.blazerod.api.resource.RenderScene
import top.fifthlight.blazerod.model.Expression
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.NodeTransform
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.model.animation.AnimationContext
import top.fifthlight.blazerod.model.animation.AnimationState
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

sealed interface ModelController {
    companion object {
        private fun RenderScene.getBone(tag: HumanoidTag) =
            humanoidTagMap[tag]?.let { node -> JointItem(nodeIndex = node.nodeIndex) }

        private fun RenderScene.getExpression(tag: Expression.Tag) =
            expressions.firstOrNull { it.tag == tag }?.let { ExpressionItem.Expression(it) }
                ?: expressionGroups.firstOrNull { it.tag == tag }?.let { ExpressionItem.Group(it) }

        private const val NANOSECONDS_PER_SECOND = 1_000_000_000L

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

        var PlayerEntityRenderState.animationPendingValues
            get() = (this as PlayerEntityRenderStateExtInternal).`armorstand$getAnimationPendingValues`()
            set(value) = (this as PlayerEntityRenderStateExtInternal).`armorstand$setAnimationPendingValues`(value)
    }

    fun update(
        uuid: UUID,
        player: AbstractClientPlayerEntity,
        renderState: PlayerEntityRenderState,
    )

    fun apply(
        uuid: UUID,
        instance: ModelInstance,
        renderState: PlayerEntityRenderState,
    )

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

    class LiveUpdated private constructor(
        private val center: JointItem?,
        private val head: JointItem?,
        private val blinkExpression: ExpressionItem?,
    ) : ModelController {
        constructor(
            scene: RenderScene,
        ) : this(
            center = scene.getBone(HumanoidTag.HIPS),
            head = scene.getBone(HumanoidTag.HEAD),
            blinkExpression = scene.getExpression(Expression.Tag.BLINK),
        )

        override fun update(
            uuid: UUID,
            player: AbstractClientPlayerEntity,
            renderState: PlayerEntityRenderState,
        ) = Unit

        override fun apply(uuid: UUID, instance: ModelInstance, renderState: PlayerEntityRenderState) {
            val sleepingDirection = renderState.sleepingDirection
            val bodyYaw = if (renderState.isInPose(EntityPose.SLEEPING) && sleepingDirection != null) {
                when (sleepingDirection) {
                    Direction.SOUTH -> 0f
                    Direction.EAST -> PI.toFloat() * 0.5f
                    Direction.NORTH -> PI.toFloat()
                    Direction.WEST -> PI.toFloat() * 1.5f
                    else -> 0f
                }
            } else {
                MathHelper.PI - renderState.bodyYaw.toRadian()
            }
            val headYaw = -renderState.relativeHeadYaw.toRadian()
            val headPitch = -renderState.pitch.toRadian()
            center?.update(instance) {
                rotation.rotationY(bodyYaw)
            }
            head?.update(instance) {
                rotation.rotationYXZ(headYaw, headPitch, 0f)
            }

            val blinkProgress = calculateBlinkProgress(
                playerUuid = uuid,
                averageBlinkInterval = 4.0,
                blinkDuration = 0.25,
                currentTime = System.nanoTime(),
            )
            blinkExpression?.apply(instance, blinkProgress)
        }
    }

    interface AnimatedModelController : ModelController {
        val animationState: AnimationState
    }

    class Predefined(
        context: AnimationContext,
        private val animationInstance: AnimationItemInstance,
    ) : AnimatedModelController {
        override val animationState: AnimationState = animationInstance.createState(context)

        override fun update(
            uuid: UUID,
            player: AbstractClientPlayerEntity,
            renderState: PlayerEntityRenderState,
        ) = AnimationContextsFactory.create().player(player).let { context ->
            animationState.updateTime(context)
            renderState.animationPendingValues = animationInstance.update(context, animationState)
        }

        override fun apply(uuid: UUID, instance: ModelInstance, renderState: PlayerEntityRenderState) {
            renderState.animationPendingValues?.let {
                animationInstance.apply(instance, it)
                renderState.animationPendingValues = null
            }
        }
    }

    class LiveSwitched private constructor(
        context: AnimationContext,
        private val animationSet: FullAnimationSet,
        private val head: JointItem?,
        private val blinkExpression: ExpressionItem?,
    ) : AnimatedModelController {
        constructor(
            context: AnimationContext,
            scene: RenderScene,
            animationSet: FullAnimationSet,
        ) : this(
            context = context,
            animationSet = animationSet,
            head = scene.getBone(HumanoidTag.HEAD),
            blinkExpression = scene.getExpression(Expression.Tag.BLINK),
        )

        sealed class PlayState {
            abstract fun getItem(set: FullAnimationSet): AnimationItemInstance
            open val loop: Boolean = true

            data class ItemActive(
                private val item: AnimationItemInstance,
            ) : PlayState() {
                override fun getItem(set: FullAnimationSet) = item
            }

            data object Idle : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.idle
            }

            data object Walking : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.walk
            }

            data object ElytraFly : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.elytraFly
            }

            data object Swimming : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.swim
            }

            data object Sleeping : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.sleep
            }

            data object Riding : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.ride
            }

            data object OnHorse : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onHorse
            }

            data object OnPig : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onPig
            }

            data object OnBoat : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onBoat
            }

            data object Dying : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.die
                override val loop: Boolean
                    get() = false
            }

            data object Sprinting : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.sprint
            }

            data object Sneaking : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.sneak
            }

            data object SneakIdle : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.sneakIdle
            }

            data object Crawling : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.crawl
            }

            data object CrawlIdle : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.crawlIdle
            }

            data object OnClimbable : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onClimbable
            }

            data object OnClimbableUp : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onClimbableUp
            }

            data object OnClimbableDown : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.onClimbableDown
            }

            data object LeftArmSwinging : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.swingLeft
            }

            data object RightArmSwinging : PlayState() {
                override fun getItem(set: FullAnimationSet) = set.swingRight
            }
        }

        private data class ActionSelection(
            val item: AnimationItemInstance,
            val arm: Arm,
        )

        private class LayeredPendingValues(
            val baseItem: AnimationItemInstance,
            val basePendingValues: AnimationItemPendingValues,
            val actionItem: AnimationItemInstance?,
            val actionPendingValues: AnimationItemPendingValues?,
            val actionArm: Arm?,
        ) : AnimationItemPendingValues

        private var playState: PlayState = PlayState.Idle
        private var item: AnimationItemInstance = playState.getItem(animationSet)
        override var animationState: AnimationState = item.createState(context)

        private var actionItem: AnimationItemInstance? = null
        private var actionAnimationState: AnimationState? = null
        private var actionArm: Arm? = null
        private var lastUsingItem: Boolean = false
        private var lastHandSwinging: Boolean = false

        private var reset = false

        private var upperBodyMaskNodeCount: Int = -1
        private var upperBodyMaskScene: RenderScene? = null
        private var leftUpperBodyMask: BooleanArray = BooleanArray(0)
        private var rightUpperBodyMask: BooleanArray = BooleanArray(0)

        companion object {
            private val horseEntityTypes = listOf(
                EntityType.HORSE,
                EntityType.DONKEY,
                EntityType.MULE,
                EntityType.LLAMA,
                EntityType.SKELETON_HORSE,
                EntityType.ZOMBIE_HORSE,
            )
        }

        private fun Arm.toHandSide() = when (this) {
            Arm.LEFT -> AnimationSet.ItemActiveKey.HandSide.LEFT
            Arm.RIGHT -> AnimationSet.ItemActiveKey.HandSide.RIGHT
        }

        private fun getItemActiveAnimation(
            itemId: net.minecraft.util.Identifier,
            arm: Arm,
            actionType: AnimationSet.ItemActiveKey.ActionType,
        ): AnimationItemInstance? {
            val key = AnimationSet.ItemActiveKey(
                itemName = itemId,
                hand = arm.toHandSide(),
                actionType = actionType,
            )
            return animationSet.itemActive[key]
        }

        private fun getSwingingHand(mainArm: Arm, arm: Arm): Hand = if (arm == mainArm) {
            Hand.MAIN_HAND
        } else {
            Hand.OFF_HAND
        }

        private fun getActionSelection(
            player: AbstractClientPlayerEntity,
            renderState: PlayerEntityRenderState,
        ): ActionSelection? {
            if (player.isDead) {
                return null
            }

            if (player.isUsingItem) {
                val usedArm = when (player.activeHand) {
                    Hand.MAIN_HAND -> renderState.mainArm
                    Hand.OFF_HAND -> if (renderState.mainArm == Arm.RIGHT) Arm.LEFT else Arm.RIGHT
                    else -> renderState.mainArm
                }
                val stack = player.getStackInHand(player.activeHand)
                val itemId = Registries.ITEM.getId(stack.item)
                val itemActive = getItemActiveAnimation(
                    itemId = itemId,
                    arm = usedArm,
                    actionType = AnimationSet.ItemActiveKey.ActionType.USING,
                ) ?: return null
                return ActionSelection(item = itemActive, arm = usedArm)
            }

            if (renderState.handSwinging) {
                return when (renderState.preferredArm) {
                    Arm.LEFT -> {
                        val stack = player.getStackInHand(getSwingingHand(renderState.mainArm, Arm.LEFT))
                        val itemId = Registries.ITEM.getId(stack.item)
                        val itemActive = getItemActiveAnimation(
                            itemId = itemId,
                            arm = Arm.LEFT,
                            actionType = AnimationSet.ItemActiveKey.ActionType.SWINGING,
                        )
                        ActionSelection(item = itemActive ?: animationSet.swingLeft, arm = Arm.LEFT)
                    }

                    Arm.RIGHT -> {
                        val stack = player.getStackInHand(getSwingingHand(renderState.mainArm, Arm.RIGHT))
                        val itemId = Registries.ITEM.getId(stack.item)
                        val itemActive = getItemActiveAnimation(
                            itemId = itemId,
                            arm = Arm.RIGHT,
                            actionType = AnimationSet.ItemActiveKey.ActionType.SWINGING,
                        )
                        ActionSelection(item = itemActive ?: animationSet.swingRight, arm = Arm.RIGHT)
                    }
                }
            }

            return null
        }

        private fun ensureUpperBodyMasks(scene: RenderScene) {
            val nodeCount = scene.nodes.size
            if (upperBodyMaskScene === scene && upperBodyMaskNodeCount == nodeCount) {
                return
            }
            upperBodyMaskScene = scene
            upperBodyMaskNodeCount = nodeCount

            fun BooleanArray.enable(tag: HumanoidTag) {
                scene.humanoidTagMap[tag]?.let { node ->
                    val idx = node.nodeIndex
                    if (idx in indices) {
                        this[idx] = true
                    }
                }
            }

            fun BooleanArray.enableAll(tags: Array<HumanoidTag>) {
                for (tag in tags) {
                    enable(tag)
                }
            }

            val torsoTags = arrayOf(
                HumanoidTag.SPINE,
                HumanoidTag.CHEST,
                HumanoidTag.UPPER_CHEST,
                HumanoidTag.NECK,
                HumanoidTag.HEAD,
            )

            val leftArmTags = arrayOf(
                HumanoidTag.LEFT_SHOULDER,
                HumanoidTag.LEFT_UPPER_ARM,
                HumanoidTag.LEFT_LOWER_ARM,
                HumanoidTag.LEFT_HAND,
                HumanoidTag.LEFT_THUMB_METACARPAL,
                HumanoidTag.LEFT_THUMB_PROXIMAL,
                HumanoidTag.LEFT_THUMB_DISTAL,
                HumanoidTag.LEFT_INDEX_PROXIMAL,
                HumanoidTag.LEFT_INDEX_INTERMEDIATE,
                HumanoidTag.LEFT_INDEX_DISTAL,
                HumanoidTag.LEFT_MIDDLE_PROXIMAL,
                HumanoidTag.LEFT_MIDDLE_INTERMEDIATE,
                HumanoidTag.LEFT_MIDDLE_DISTAL,
                HumanoidTag.LEFT_RING_PROXIMAL,
                HumanoidTag.LEFT_RING_INTERMEDIATE,
                HumanoidTag.LEFT_RING_DISTAL,
                HumanoidTag.LEFT_LITTLE_PROXIMAL,
                HumanoidTag.LEFT_LITTLE_INTERMEDIATE,
                HumanoidTag.LEFT_LITTLE_DISTAL,
            )

            val rightArmTags = arrayOf(
                HumanoidTag.RIGHT_SHOULDER,
                HumanoidTag.RIGHT_UPPER_ARM,
                HumanoidTag.RIGHT_LOWER_ARM,
                HumanoidTag.RIGHT_HAND,
                HumanoidTag.RIGHT_THUMB_METACARPAL,
                HumanoidTag.RIGHT_THUMB_PROXIMAL,
                HumanoidTag.RIGHT_THUMB_DISTAL,
                HumanoidTag.RIGHT_INDEX_PROXIMAL,
                HumanoidTag.RIGHT_INDEX_INTERMEDIATE,
                HumanoidTag.RIGHT_INDEX_DISTAL,
                HumanoidTag.RIGHT_MIDDLE_PROXIMAL,
                HumanoidTag.RIGHT_MIDDLE_INTERMEDIATE,
                HumanoidTag.RIGHT_MIDDLE_DISTAL,
                HumanoidTag.RIGHT_RING_PROXIMAL,
                HumanoidTag.RIGHT_RING_INTERMEDIATE,
                HumanoidTag.RIGHT_RING_DISTAL,
                HumanoidTag.RIGHT_LITTLE_PROXIMAL,
                HumanoidTag.RIGHT_LITTLE_INTERMEDIATE,
                HumanoidTag.RIGHT_LITTLE_DISTAL,
            )

            leftUpperBodyMask = BooleanArray(nodeCount)
            rightUpperBodyMask = BooleanArray(nodeCount)
            leftUpperBodyMask.enableAll(torsoTags)
            rightUpperBodyMask.enableAll(torsoTags)
            leftUpperBodyMask.enableAll(leftArmTags)
            rightUpperBodyMask.enableAll(rightArmTags)
        }

        private fun getBaseState(
            player: AbstractClientPlayerEntity,
            renderState: PlayerEntityRenderState,
        ): PlayState {
            val vehicleType = player.vehicle?.type

            return when {
                player.isDead -> PlayState.Dying

                vehicleType in horseEntityTypes -> PlayState.OnHorse
                vehicleType == EntityType.PIG -> PlayState.OnPig
                vehicleType?.isIn(EntityTypeTags.BOAT) == true -> PlayState.OnBoat
                vehicleType != null -> PlayState.Riding

                renderState.pose == EntityPose.SLEEPING -> PlayState.Sleeping
                renderState.pose == EntityPose.GLIDING -> PlayState.ElytraFly
                player.isSwimming -> PlayState.Swimming
                renderState.pose == EntityPose.SWIMMING -> if (player.movement.horizontalLength() > .01) {
                    PlayState.Crawling
                } else {
                    PlayState.CrawlIdle
                }

                player.isClimbing -> when {
                    player.movement.y > 0.1 -> PlayState.OnClimbableUp
                    player.movement.y < -0.1 -> PlayState.OnClimbableDown
                    player.isSneaking -> PlayState.OnClimbable
                    else -> PlayState.Idle
                }

                renderState.pose == EntityPose.CROUCHING -> if (player.movement.horizontalLength() > .01) {
                    PlayState.Sneaking
                } else {
                    PlayState.SneakIdle
                }

                player.isSprinting -> PlayState.Sprinting

                player.movement.horizontalLength() > .05 -> PlayState.Walking
                else -> PlayState.Idle
            }
        }

        override fun update(
            uuid: UUID,
            player: AbstractClientPlayerEntity,
            renderState: PlayerEntityRenderState,
        ): Unit = AnimationContextsFactory.create().player(player).let { context ->
            val newState = getBaseState(player, renderState)
            if (newState != playState) {
                playState = newState
            }
            val newItem = newState.getItem(animationSet)
            if (newItem != item) {
                item = newItem
                animationState = newItem.createState(context)
                reset = true
            }
            animationState.updateTime(context)
            val basePending = item.update(context, animationState)

            val actionSelection = getActionSelection(player, renderState)
            if (actionSelection == null) {
                actionItem = null
                actionAnimationState = null
                actionArm = null
                lastUsingItem = player.isUsingItem
                lastHandSwinging = renderState.handSwinging
                renderState.animationPendingValues = LayeredPendingValues(
                    baseItem = item,
                    basePendingValues = basePending,
                    actionItem = null,
                    actionPendingValues = null,
                    actionArm = null,
                )
                return@let
            }

            val needRestartByEdge = (player.isUsingItem && !lastUsingItem) || (renderState.handSwinging && !lastHandSwinging)

            if (needRestartByEdge || actionSelection.item != actionItem || actionSelection.arm != actionArm) {
                actionItem = actionSelection.item
                actionAnimationState = actionSelection.item.createState(context)
                actionArm = actionSelection.arm
            }

            val actionItem = actionItem
            val actionAnimationState = actionAnimationState
            val actionArm = actionArm
            if (actionItem == null || actionAnimationState == null || actionArm == null) {
                lastUsingItem = player.isUsingItem
                lastHandSwinging = renderState.handSwinging
                renderState.animationPendingValues = LayeredPendingValues(
                    baseItem = item,
                    basePendingValues = basePending,
                    actionItem = null,
                    actionPendingValues = null,
                    actionArm = null,
                )
                return@let
            }

            actionAnimationState.updateTime(context)
            val actionPending = actionItem.update(context, actionAnimationState)
            renderState.animationPendingValues = LayeredPendingValues(
                baseItem = item,
                basePendingValues = basePending,
                actionItem = actionItem,
                actionPendingValues = actionPending,
                actionArm = actionArm,
            )

            lastUsingItem = player.isUsingItem
            lastHandSwinging = renderState.handSwinging
        }

        override fun apply(uuid: UUID, instance: ModelInstance, renderState: PlayerEntityRenderState) {
            if (reset) {
                instance.clearTransform()
                reset = false
            }
            val pending = renderState.animationPendingValues
            when (pending) {
                is LayeredPendingValues -> {
                    pending.baseItem.apply(instance, pending.basePendingValues)
                    if (pending.actionItem != null && pending.actionPendingValues != null && pending.actionArm != null) {
                        ensureUpperBodyMasks(instance.scene)
                        val mask = if (pending.actionArm == Arm.LEFT) {
                            leftUpperBodyMask
                        } else {
                            rightUpperBodyMask
                        }
                        val actionImpl = pending.actionItem as? AnimationItemInstanceImpl
                        if (actionImpl != null) {
                            actionImpl.applyMasked(instance, pending.actionPendingValues, mask)
                        } else {
                            pending.actionItem.apply(instance, pending.actionPendingValues)
                        }
                    }
                    renderState.animationPendingValues = null
                }

                null -> Unit

                else -> {
                    item.apply(instance, pending)
                    renderState.animationPendingValues = null
                }
            }

            val sleepingDirection = renderState.sleepingDirection
            val bodyYaw = if (renderState.isInPose(EntityPose.SLEEPING) && sleepingDirection != null) {
                when (sleepingDirection) {
                    Direction.SOUTH -> 0f
                    Direction.EAST -> PI.toFloat() * 0.5f
                    Direction.NORTH -> PI.toFloat()
                    Direction.WEST -> PI.toFloat() * 1.5f
                    else -> 0f
                }
            } else {
                MathHelper.PI - renderState.bodyYaw.toRadian()
            }

            val headYaw = -renderState.relativeHeadYaw.toRadian()
            val headPitch = -renderState.pitch.toRadian()
            instance.setTransformDecomposed(instance.scene.rootNode.nodeIndex, TransformId.RELATIVE_ANIMATION) {
                rotation.rotationY(bodyYaw)
            }
            head?.update(instance) {
                rotation.rotationYXZ(headYaw, headPitch, 0f)
            }

            val blinkProgress = calculateBlinkProgress(
                playerUuid = uuid,
                averageBlinkInterval = 4.0,
                blinkDuration = 0.25,
                currentTime = System.nanoTime(),
            )
            blinkExpression?.apply(instance, blinkProgress)
        }
    }

    class Vmc(
        private val scene: RenderScene,
    ) : ModelController {
        private val bones = mutableMapOf<HumanoidTag, Optional<JointItem>>()
        private val expressions = mutableMapOf<Expression.Tag, Optional<ExpressionItem>>()

        override fun update(uuid: UUID, player: AbstractClientPlayerEntity, renderState: PlayerEntityRenderState) = Unit

        override fun apply(uuid: UUID, instance: ModelInstance, renderState: PlayerEntityRenderState) {
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
