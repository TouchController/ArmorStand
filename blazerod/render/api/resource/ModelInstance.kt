package top.fifthlight.blazerod.api.resource

import net.minecraft.client.render.VertexConsumerProvider
import org.joml.Matrix4f
import org.joml.Matrix4fc
import top.fifthlight.blazerod.api.refcount.RefCount
import top.fifthlight.blazerod.model.NodeTransform
import top.fifthlight.blazerod.model.NodeTransformView
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.mergetools.api.ExpectFactory
import java.util.function.Consumer

interface ModelInstance : RefCount {
    val scene: RenderScene

    fun clearTransform()
    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, matrix: Matrix4f)
    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, updater: Consumer<NodeTransform.Matrix>)
    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, updater: NodeTransform.Matrix.() -> Unit)
    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, decomposed: NodeTransformView.Decomposed)
    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, updater: Consumer<NodeTransform.Decomposed>)
    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, updater: NodeTransform.Decomposed.() -> Unit)
    fun setTransformBedrock(nodeIndex: Int, transformId: TransformId, updater: NodeTransform.Bedrock.() -> Unit)
    fun getIkEnabled(index: Int): Boolean
    fun setIkEnabled(index: Int, enabled: Boolean)
    fun setGroupWeight(morphedPrimitiveIndex: Int, targetGroupIndex: Int, weight: Float)

    fun getCameraTransform(index: Int): CameraTransform?

    fun updateCamera()
    fun debugRender(viewProjectionMatrix: Matrix4fc, consumers: VertexConsumerProvider, time: Float)
    fun updateRenderData(time: Float)

    fun createRenderTask(
        modelMatrix: Matrix4fc,
        light: Int,
        overlay: Int = 0,
    ): RenderTask

    @ExpectFactory
    interface Factory {
        fun of(scene: RenderScene): ModelInstance
    }
}