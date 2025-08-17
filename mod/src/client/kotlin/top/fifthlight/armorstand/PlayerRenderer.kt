package top.fifthlight.armorstand

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.state.ModelInstanceManager
import top.fifthlight.armorstand.util.RendererManager
import top.fifthlight.blazerod.model.renderer.InstancedRenderer
import top.fifthlight.blazerod.model.resource.CameraTransform
import top.fifthlight.blazerod.model.resource.RenderCamera
import top.fifthlight.blazerod.util.TimeUtil
import java.lang.ref.WeakReference
import java.util.*

object PlayerRenderer {
    private var renderingWorld = false

    private var prevModelItem = WeakReference<ModelInstanceManager.ModelInstanceItem.Model?>(null)
    val selectedCameraIndex = MutableStateFlow<Int?>(null)
    private val _totalCameras = MutableStateFlow<List<RenderCamera>?>(listOf())
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
        entry.controller.apply(instance)
        instance.updateCamera()

        return instance.modelData.cameraTransforms.getOrNull(selectedIndex).also {
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

    @JvmStatic
    fun appendPlayer(
        uuid: UUID,
        vanillaState: PlayerEntityRenderState,
        matrixStack: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ): Boolean {
        val time = System.nanoTime().toFloat() / TimeUtil.NANOSECONDS_PER_SECOND.toFloat()
        val entry = ModelInstanceManager.get(uuid, System.nanoTime())
        if (entry !is ModelInstanceManager.ModelInstanceItem.Model) {
            return false
        }

        val controller = entry.controller
        val instance = entry.instance

        controller.update(uuid, vanillaState)
        controller.apply(instance)
        instance.updateRenderData(time)

        val backupItem = matrixStack.peek().copy()
        matrixStack.pop()
        matrixStack.push()

        if (ArmorStandClient.debugBone) {
            instance.debugRender(matrixStack.peek().positionMatrix, consumers, time)
        } else {
            matrix.set(matrixStack.peek().positionMatrix)
            matrix.scale(ConfigHolder.config.value.modelScale)
            matrix.mulLocal(RenderSystem.getModelViewStack())
            val currentRenderer = RendererManager.currentRenderer
            val task = instance.createRenderTask(matrix, light)
            if (currentRenderer is InstancedRenderer<*, *> && renderingWorld) {
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
        RendererManager.currentRendererInstanced?.let { renderer ->
            val colorFrameBuffer = RenderSystem.outputColorTextureOverride ?: mainTarget.colorAttachmentView!!
            val depthFrameBuffer = RenderSystem.outputDepthTextureOverride ?: mainTarget.depthAttachmentView
            renderer.executeTasks(colorFrameBuffer, depthFrameBuffer)
        }
    }

    fun endFrame() {
        cameraTransform = null
    }
}
