package top.fifthlight.armorstand

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.EntityPose
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.Arm
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.state.ModelInstanceManager
import top.fifthlight.armorstand.util.RendererManager
import top.fifthlight.blazerod.api.render.ScheduledRenderer
import top.fifthlight.blazerod.api.resource.CameraTransform
import top.fifthlight.blazerod.api.resource.ModelInstance
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.Camera
import java.lang.ref.WeakReference
import java.util.*

object PlayerRenderer {
    private const val NANOSECONDS_PER_SECOND = 1_000_000_000L
    private val startNanoTime = System.nanoTime()
    private var renderingWorld = false

    private val handWorldMatrix = Matrix4f()
    private val handWorldNoScaleMatrix = Matrix4f()
    private val handWorldPos = Vector3f()
    private val handWorldRot = Quaternionf()
    private val itemLocalMatrix = Matrix4f()
    private val itemNormalMatrix = Matrix3f()

    private var prevModelItem = WeakReference<ModelInstanceManager.ModelInstanceItem.Model?>(null)
    val selectedCameraIndex = MutableStateFlow<Int?>(null)
    private val _totalCameras = MutableStateFlow<List<Camera>?>(listOf())
    val totalCameras = _totalCameras.asStateFlow()
    private var cameraTransform: CameraTransform? = null

    @JvmStatic
    fun getCurrentCameraTransform(): CameraTransform? {
        cameraTransform?.let { return it }
        val entry = ModelInstanceManager.getSelfItem(load = false) ?: return null
        if (prevModelItem.get() != entry) {
            selectedCameraIndex.value = null
            if (entry is ModelInstanceManager.ModelInstanceItem.Model) {
                _totalCameras.value = entry.instance.scene.cameras
                prevModelItem = WeakReference(entry)
            } else {
                _totalCameras.value = listOf()
            }
            return null
        }

        val selectedIndex = selectedCameraIndex.value ?: return null
        val instance = entry.instance
        instance.updateCamera()

        return instance.getCameraTransform(selectedIndex).also {
            cameraTransform = it
        } ?: run {
            selectedCameraIndex.value = null
            null
        }
    }

    fun startRenderWorld() {
        renderingWorld = true
    }

    private val matrix = Matrix4f()

    private fun renderHeldItem(
        instance: ModelInstance,
        itemState: ItemRenderState,
        player: LivingEntity?,
        itemStack: ItemStack?,
        displayContext: ItemDisplayContext,
        tag: HumanoidTag,
        matrixStack: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        if ((itemStack == null || itemStack.isEmpty) && itemState.isEmpty) {
            return
        }

        val node = instance.scene.humanoidTagMap[tag] ?: return
        instance.copyNodeWorldTransform(node.nodeIndex, handWorldMatrix)
        handWorldMatrix.getTranslation(handWorldPos)
        handWorldMatrix.getUnnormalizedRotation(handWorldRot)
        handWorldRot.normalize()
        handWorldNoScaleMatrix.translationRotate(
            handWorldPos.x,
            handWorldPos.y,
            handWorldPos.z,
            handWorldRot.x,
            handWorldRot.y,
            handWorldRot.z,
            handWorldRot.w,
        )

        itemLocalMatrix.identity()
        itemLocalMatrix.scale(ConfigHolder.config.value.modelScale)
        instance.scene.renderTransform?.applyOnMatrix(itemLocalMatrix)
        itemLocalMatrix.mul(handWorldNoScaleMatrix)
        itemLocalMatrix.getTranslation(handWorldPos)
        itemLocalMatrix.getUnnormalizedRotation(handWorldRot)
        handWorldRot.normalize()
        itemLocalMatrix.translationRotate(
            handWorldPos.x,
            handWorldPos.y,
            handWorldPos.z,
            handWorldRot.x,
            handWorldRot.y,
            handWorldRot.z,
            handWorldRot.w,
        )

        matrixStack.push()
        matrixStack.multiplyPositionMatrix(itemLocalMatrix)
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f))
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        val handSign = if (tag == HumanoidTag.LEFT_HAND) -1.0 else 1.0
        matrixStack.translate(handSign / 16.0, 0.125, -0.625)
        matrixStack.peek().normalMatrix.set(
            itemNormalMatrix.set(matrixStack.peek().positionMatrix).invert().transpose()
        )
        if (player != null && itemStack != null && !itemStack.isEmpty) {
            MinecraftClient.getInstance().itemRenderer.renderItem(
                player,
                itemStack,
                displayContext,
                matrixStack,
                consumers,
                player.world,
                light,
                overlay,
                0,
            )
        } else {
            itemState.render(matrixStack, consumers, light, overlay)
        }
        matrixStack.pop()
    }

    @JvmStatic
    fun updatePlayer(
        player: AbstractClientPlayerEntity,
        state: PlayerEntityRenderState,
    ) {
        val uuid = player.uuid
        val entry = ModelInstanceManager.get(uuid, System.nanoTime())
        if (entry !is ModelInstanceManager.ModelInstanceItem.Model) {
            return
        }

        val controller = entry.controller
        controller.update(uuid, player, state)
    }

    @JvmStatic
    fun appendPlayer(
        uuid: UUID,
        vanillaState: PlayerEntityRenderState,
        matrixStack: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ): Boolean {
        val entry = ModelInstanceManager.get(uuid, System.nanoTime())
        if (entry !is ModelInstanceManager.ModelInstanceItem.Model) {
            return false
        }

        val controller = entry.controller
        val instance = entry.instance

        val player = MinecraftClient.getInstance().world?.getPlayerByUuid(uuid)
        val mainHandStack = player?.mainHandStack
        val offHandStack = player?.offHandStack
        val rightHandStack: ItemStack? = if (vanillaState.mainArm == Arm.RIGHT) mainHandStack else offHandStack
        val leftHandStack: ItemStack? = if (vanillaState.mainArm == Arm.RIGHT) offHandStack else mainHandStack

        val time = (System.nanoTime() - startNanoTime).toFloat() / NANOSECONDS_PER_SECOND.toFloat()
        controller.apply(uuid, instance, vanillaState)
        instance.updateRenderData(time)

        val backupItem = matrixStack.peek().copy()
        matrixStack.pop()
        matrixStack.push()

        if (vanillaState.pose == EntityPose.CROUCHING) {
            matrixStack.translate(0.0, 0.125, 0.0)
        }

        if (ArmorStandClient.instance.debugBone) {
            instance.debugRender(matrixStack.peek().positionMatrix, consumers, time)
        } else {
            matrix.set(matrixStack.peek().positionMatrix)
            matrix.scale(ConfigHolder.config.value.modelScale)
            val currentRenderer = RendererManager.currentRenderer
            val task = instance.createRenderTask(matrix, light, overlay)
            if (currentRenderer is ScheduledRenderer<*, *> && renderingWorld) {
                currentRenderer.schedule(task)
            } else {
                val mainTarget = MinecraftClient.getInstance().framebuffer
                val colorFrameBuffer = RenderSystem.outputColorTextureOverride ?: mainTarget.colorAttachmentView!!
                val depthFrameBuffer = RenderSystem.outputDepthTextureOverride ?: mainTarget.depthAttachmentView
                currentRenderer.render(
                    colorFrameBuffer = colorFrameBuffer,
                    depthFrameBuffer = depthFrameBuffer,
                    scene = instance.scene,
                    task = task,
                )
                task.release()
            }

            renderHeldItem(
                instance = instance,
                itemState = vanillaState.rightHandItemState,
                player = player,
                itemStack = rightHandStack,
                displayContext = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                tag = HumanoidTag.RIGHT_HAND,
                matrixStack = matrixStack,
                consumers = consumers,
                light = light,
                overlay = overlay,
            )
            renderHeldItem(
                instance = instance,
                itemState = vanillaState.leftHandItemState,
                player = player,
                itemStack = leftHandStack,
                displayContext = ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                tag = HumanoidTag.LEFT_HAND,
                matrixStack = matrixStack,
                consumers = consumers,
                light = light,
                overlay = overlay,
            )
        }

        matrixStack.pop()
        matrixStack.push()
        matrixStack.peek().apply {
            positionMatrix.set(backupItem.positionMatrix)
            normalMatrix.set(backupItem.normalMatrix)
        }
        return true
    }

    fun executeDraw() {
        renderingWorld = false
        val mainTarget = MinecraftClient.getInstance().framebuffer
        RendererManager.currentRendererScheduled?.let { renderer ->
            val colorFrameBuffer = RenderSystem.outputColorTextureOverride ?: mainTarget.colorAttachmentView!!
            val depthFrameBuffer = RenderSystem.outputDepthTextureOverride ?: mainTarget.depthAttachmentView
            renderer.executeTasks(colorFrameBuffer, depthFrameBuffer)
        }
    }

    fun endFrame() {
        cameraTransform = null
    }
}
