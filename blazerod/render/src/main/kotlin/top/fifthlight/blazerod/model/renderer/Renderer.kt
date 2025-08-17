package top.fifthlight.blazerod.model.renderer

import com.mojang.blaze3d.textures.GpuTextureView
import top.fifthlight.blazerod.model.RenderScene
import top.fifthlight.blazerod.model.RenderTask
import top.fifthlight.blazerod.model.TaskMap
import top.fifthlight.blazerod.model.data.MorphTargetBuffer
import top.fifthlight.blazerod.model.data.RenderSkinBuffer
import top.fifthlight.blazerod.model.node.component.PrimitiveNodeComponent
import top.fifthlight.blazerod.model.resource.RenderPrimitive

sealed class Renderer<R : Renderer<R, T>, T : Renderer.Type<R, T>> : AutoCloseable {
    sealed class Type<R : Renderer<R, T>, T : Type<R, T>> {
        abstract val isAvailable: Boolean
        abstract val supportInstancing: Boolean
        abstract fun create(): R
    }

    abstract val type: T

    open fun render(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
        task: RenderTask,
        scene: RenderScene,
    ) {
        val instance = task.instance
        for (component in scene.primitiveComponents) {
            render(
                colorFrameBuffer = colorFrameBuffer,
                depthFrameBuffer = depthFrameBuffer,
                scene = scene,
                primitive = component.primitive,
                primitiveIndex = component.primitiveIndex,
                task = task,
                skinBuffer = component.skinIndex?.let {
                    (instance.modelData.skinBuffers.getOrNull(it)?.content
                        ?: error("Has skin but no skin buffer"))
                },
                targetBuffer = component.morphedPrimitiveIndex?.let {
                    (instance.modelData.targetBuffers.getOrNull(it)?.content
                        ?: error("Has morph target but no morph target buffer"))
                },
            )
        }
    }

    abstract fun render(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
        scene: RenderScene,
        primitive: RenderPrimitive,
        primitiveIndex: Int,
        task: RenderTask,
        skinBuffer: RenderSkinBuffer?,
        targetBuffer: MorphTargetBuffer?,
    )

    abstract fun rotate()
}

sealed class InstancedRenderer<R : InstancedRenderer<R, T>, T : Renderer.Type<R, T>> : Renderer<R, T>() {
    abstract fun schedule(task: RenderTask)

    abstract fun executeTasks(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
    )
}

abstract class TaskMapInstancedRenderer<R : InstancedRenderer<R, T>, T : Renderer.Type<R, T>> : InstancedRenderer<R, T>() {
    private val taskMap = TaskMap()

    override fun schedule(task: RenderTask) = taskMap.addTask(task)

    override fun executeTasks(colorFrameBuffer: GpuTextureView, depthFrameBuffer: GpuTextureView?) {
        taskMap.executeTasks { scene, tasks ->
            when (tasks.size) {
                0 -> {}
                1 -> {
                    render(colorFrameBuffer, depthFrameBuffer, tasks[0], scene)
                }

                else -> {
                    for (component in scene.primitiveComponents) {
                        renderInstanced(
                            colorFrameBuffer = colorFrameBuffer,
                            depthFrameBuffer = depthFrameBuffer,
                            tasks = tasks,
                            scene = scene,
                            component = component,
                        )
                    }
                }
            }
        }
    }

    abstract fun renderInstanced(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
        tasks: List<RenderTask>,
        scene: RenderScene,
        component: PrimitiveNodeComponent,
    )
}
