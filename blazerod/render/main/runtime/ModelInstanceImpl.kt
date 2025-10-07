package top.fifthlight.blazerod.runtime

import net.minecraft.client.render.VertexConsumerProvider
import org.joml.Matrix4f
import org.joml.Matrix4fc
import top.fifthlight.blazerod.api.refcount.AbstractRefCount
import top.fifthlight.blazerod.api.resource.ModelInstance
import top.fifthlight.blazerod.api.resource.RenderScene
import top.fifthlight.blazerod.model.NodeTransform
import top.fifthlight.blazerod.model.NodeTransformView
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.runtime.data.LocalMatricesBuffer
import top.fifthlight.blazerod.runtime.data.MorphTargetBuffer
import top.fifthlight.blazerod.runtime.data.RenderSkinBuffer
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.TransformMap
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import top.fifthlight.blazerod.runtime.node.markNodeTransformDirty
import top.fifthlight.blazerod.runtime.resource.CameraTransformImpl
import top.fifthlight.blazerod.util.cowbuffer.CowBuffer
import top.fifthlight.blazerod.util.cowbuffer.copy
import top.fifthlight.blazerod.util.iterator.mapToArray
import top.fifthlight.mergetools.api.ActualConstructor
import top.fifthlight.mergetools.api.ActualImpl
import java.util.function.Consumer

@ActualImpl(ModelInstance::class)
class ModelInstanceImpl(
    override val scene: RenderSceneImpl,
) : AbstractRefCount(), ModelInstance {
    companion object {
        private const val PHYSICS_FPS = 120f
    }

    @ActualConstructor("of")
    constructor(scene: RenderScene) : this(scene as RenderSceneImpl)

    override val typeId: String
        get() = "model_instance"

    val modelData = ModelData(scene)

    init {
        scene.increaseReferenceCount()
        scene.attachToInstance(this)
    }

    class ModelData(scene: RenderSceneImpl) : AutoCloseable {
        var undirtyNodeCount = 0

        val transformMaps = scene.nodes.mapToArray { node ->
            TransformMap(node.absoluteTransform)
        }

        val transformDirty = Array(scene.nodes.size) { true }

        val worldTransforms = Array(scene.nodes.size) { Matrix4f() }

        val localMatricesBuffer = run {
            val buffer = LocalMatricesBuffer(scene.primitiveComponents.size)
            buffer.clear()
            CowBuffer.acquire(buffer).also { it.increaseReferenceCount() }
        }

        val skinBuffers = scene.skins.mapIndexed { index, skin ->
            val skinBuffer = RenderSkinBuffer(skin.jointSize)
            skinBuffer.clear()
            CowBuffer.acquire(skinBuffer).also { it.increaseReferenceCount() }
        }

        val targetBuffers = scene.morphedPrimitiveComponents.mapIndexed { index, component ->
            val primitive = component.primitive
            val targets = primitive.targets!!
            val targetBuffers = MorphTargetBuffer(
                positionTargets = targets.position.targetsCount,
                colorTargets = targets.color.targetsCount,
                texCoordTargets = targets.texCoord.targetsCount,
            )
            for (targetGroup in primitive.targetGroups) {
                fun processGroup(index: Int?, channel: MorphTargetBuffer.WeightChannel, weight: Float) =
                    index?.let {
                        channel[index] = weight
                    }
                processGroup(targetGroup.position, targetBuffers.positionChannel, targetGroup.weight)
                processGroup(targetGroup.color, targetBuffers.colorChannel, targetGroup.weight)
                processGroup(targetGroup.texCoord, targetBuffers.texCoordChannel, targetGroup.weight)
            }
            CowBuffer.acquire(targetBuffers).also { it.increaseReferenceCount() }
        }

        val cameraTransforms = scene.cameras.map { CameraTransformImpl.of(it) }

        val ikEnabled = Array(scene.ikTargetData.size) { true }

        override fun close() {
            localMatricesBuffer.decreaseReferenceCount()
            skinBuffers.forEach { it.decreaseReferenceCount() }
            targetBuffers.forEach { it.decreaseReferenceCount() }
        }
    }

    override fun clearTransform() {
        modelData.undirtyNodeCount = 0
        for (i in scene.nodes.indices) {
            modelData.transformMaps[i].clearFrom(TransformId.ABSOLUTE.next)
            modelData.transformDirty[i] = true
        }
    }

    override fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, matrix: Matrix4f) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.setMatrix(transformId, matrix)
    }

    override fun setTransformDecomposed(
        nodeIndex: Int,
        transformId: TransformId,
        decomposed: NodeTransformView.Decomposed,
    ) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.setMatrix(transformId, decomposed)
    }

    override fun setTransformDecomposed(
        nodeIndex: Int,
        transformId: TransformId,
        updater: Consumer<NodeTransform.Decomposed>,
    ) =
        setTransformDecomposed(nodeIndex, transformId) { updater.accept(this) }

    override fun setTransformDecomposed(
        nodeIndex: Int,
        transformId: TransformId,
        updater: NodeTransform.Decomposed.() -> Unit,
    ) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.updateDecomposed(transformId, updater)
    }

    override fun setTransformBedrock(
        nodeIndex: Int,
        transformId: TransformId,
        updater: NodeTransform.Bedrock.() -> Unit,
    ) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.updateBedrock(transformId, updater)
    }

    override fun getIkEnabled(index: Int) = modelData.ikEnabled[index]

    override fun setIkEnabled(index: Int, enabled: Boolean) {
        val prevEnabled = modelData.ikEnabled[index]
        modelData.ikEnabled[index] = enabled
        if (prevEnabled && !enabled) {
            val component = scene.ikTargetComponents[index]
            for (chain in component.chains) {
                markNodeTransformDirty(scene.nodes[chain.nodeIndex])
                val transform = modelData.transformMaps[chain.nodeIndex]
                transform.clearFrom(component.transformId)
            }
        }
    }

    override fun setGroupWeight(morphedPrimitiveIndex: Int, targetGroupIndex: Int, weight: Float) {
        val primitiveComponent = scene.morphedPrimitiveComponents[morphedPrimitiveIndex]
        val group = primitiveComponent.primitive.targetGroups[targetGroupIndex]
        val weightsIndex = requireNotNull(primitiveComponent.morphedPrimitiveIndex) {
            "Component $primitiveComponent don't have target? Check model loader"
        }
        val weights = modelData.targetBuffers[weightsIndex]
        weights.edit {
            group.position?.let { positionChannel[it] = weight }
            group.color?.let { colorChannel[it] = weight }
            group.texCoord?.let { texCoordChannel[it] = weight }
        }
    }

    override fun getCameraTransform(index: Int) = modelData.cameraTransforms.getOrNull(index)

    override fun updateCamera() {
        scene.updateCamera(this)
    }

    override fun debugRender(viewProjectionMatrix: Matrix4fc, consumers: VertexConsumerProvider) {
        scene.debugRender(this, viewProjectionMatrix, consumers)
    }

    override fun updateRenderData() {
        scene.updateRenderData(this)
    }

    internal fun updateNodeTransform(nodeIndex: Int) {
        val node = scene.nodes[nodeIndex]
        updateNodeTransform(node)
    }

    internal fun updateNodeTransform(node: RenderNodeImpl) {
        if (modelData.undirtyNodeCount == scene.nodes.size) {
            return
        }
        node.update(UpdatePhase.GlobalTransformPropagation, node, this)
        for (child in node.children) {
            updateNodeTransform(child)
        }
    }

    override fun createRenderTask(
        modelMatrix: Matrix4fc,
        light: Int,
        overlay: Int,
    ): RenderTaskImpl {
        return RenderTaskImpl.acquire(
            instance = this,
            modelMatrix = modelMatrix,
            light = light,
            overlay = overlay,
            localMatricesBuffer = modelData.localMatricesBuffer.copy(),
            skinBuffer = modelData.skinBuffers.copy(),
            morphTargetBuffer = modelData.targetBuffers.copy().also { buffer ->
                // Upload indices don't change the actual data
                buffer.forEach {
                    it.content.uploadIndices()
                }
            },
        ).apply {
            scene.renderTransform?.matrix?.let {
                this.modelMatrix.mul(it)
            }
        }
    }

    override fun onClosed() {
        scene.decreaseReferenceCount()
        modelData.close()
    }
}
