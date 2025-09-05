package top.fifthlight.blazerod.model.renderer

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPassImpl
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.OverlayTexture
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.joml.Vector4f
import top.fifthlight.blazerod.BlazeRod
import top.fifthlight.blazerod.extension.*
import top.fifthlight.blazerod.model.RenderScene
import top.fifthlight.blazerod.model.RenderTask
import top.fifthlight.blazerod.model.data.MorphTargetBuffer
import top.fifthlight.blazerod.model.data.RenderSkinBuffer
import top.fifthlight.blazerod.model.node.component.PrimitiveNodeComponent
import top.fifthlight.blazerod.model.resource.RenderMaterial
import top.fifthlight.blazerod.model.resource.RenderPrimitive
import top.fifthlight.blazerod.model.toVector4f
import top.fifthlight.blazerod.model.uniform.ComputeDataUniformBuffer
import top.fifthlight.blazerod.model.uniform.MorphDataUniformBuffer
import top.fifthlight.blazerod.model.uniform.SkinModelIndicesUniformBuffer
import top.fifthlight.blazerod.pipeline.ComputePipeline
import top.fifthlight.blazerod.render.BlazerodVertexFormats
import top.fifthlight.blazerod.render.setIndexBuffer
import top.fifthlight.blazerod.systems.ComputePass
import top.fifthlight.blazerod.util.BitmapItem
import top.fifthlight.blazerod.util.GpuShaderDataPool
import top.fifthlight.blazerod.util.IrisApiWrapper
import top.fifthlight.blazerod.util.ObjectPool
import top.fifthlight.blazerod.util.ceilDiv
import top.fifthlight.blazerod.util.ofSsbo
import top.fifthlight.blazerod.util.upload
import java.util.*
import kotlin.collections.getOrPut

class ComputeShaderTransformRenderer private constructor() :
    ScheduledRenderer<ComputeShaderTransformRenderer, ComputeShaderTransformRenderer.Type>() {

    @Suppress("NOTHING_TO_INLINE")
    @JvmInline
    private value class PipelineInfo(val bitmap: BitmapItem = BitmapItem()) {
        constructor(
            skinned: Boolean = false,
            irisVertexFormat: Boolean = false,
            morphed: Boolean = false,
        ) : this(Unit.run {
            var item = BitmapItem()
            if (skinned) {
                item += ELEMENT_SKINNED
            }
            if (irisVertexFormat) {
                item += ELEMENT_IRIS_VERTEX_FORMAT
            }
            if (morphed) {
                item += ELEMENT_MORPHED
            }
            item
        })

        constructor(
            material: RenderMaterial<*>,
            irisVertexFormat: Boolean,
        ) : this(
            skinned = material.skinned,
            irisVertexFormat = irisVertexFormat,
            morphed = material.morphed
        )

        val skinned
            get() = ELEMENT_SKINNED in bitmap
        val irisVertexFormat
            get() = ELEMENT_IRIS_VERTEX_FORMAT in bitmap
        val morphed
            get() = ELEMENT_MORPHED in bitmap

        fun nameSuffix() = buildString {
            if (skinned) {
                append("_skinned")
            }
            if (irisVertexFormat) {
                append("_iris_vertex_format")
            }
            if (morphed) {
                append("_morphed")
            }
        }

        companion object {
            val ELEMENT_SKINNED = BitmapItem.Element.of(0)
            val ELEMENT_IRIS_VERTEX_FORMAT = BitmapItem.Element.of(1)
            val ELEMENT_MORPHED = BitmapItem.Element.of(2)
        }

        inline operator fun plus(element: BitmapItem.Element) =
            PipelineInfo(bitmap + element)

        inline operator fun minus(element: BitmapItem.Element) =
            PipelineInfo(bitmap - element)

        inline operator fun contains(element: BitmapItem.Element) =
            element in bitmap

        override fun toString(): String {
            return "PipelineInfo(skinned=$skinned, irisVertexFormat=$irisVertexFormat, morphed=$morphed)"
        }
    }

    companion object Type : Renderer.Type<ComputeShaderTransformRenderer, Type>() {
        override val isAvailable: Boolean by lazy {
            val device = RenderSystem.getDevice()
            device.supportSsbo && device.supportComputeShader && device.supportMemoryBarrier && device.supportShaderPacking
        }

        override val supportScheduling: Boolean
            get() = true

        override fun create() = ComputeShaderTransformRenderer()

        private val pipelineCache = mutableMapOf<RenderMaterial.Descriptor, Int2ReferenceMap<ComputePipeline>>()

        private fun getPipeline(material: RenderMaterial<*>, irisVertexFormat: Boolean): ComputePipeline {
            val pipelineInfo = PipelineInfo(
                material = material,
                irisVertexFormat = irisVertexFormat,
            )
            val materialMap = pipelineCache.getOrPut(material.descriptor) { Int2ReferenceAVLTreeMap() }
            return materialMap.getOrPut(pipelineInfo.bitmap.inner) {
                ComputePipeline.builder().apply {
                    withLocation(Identifier.of("blazerod", "vertex_transform" + pipelineInfo.nameSuffix()))
                    withComputeShader(Identifier.of("blazerod", "compute/vertex_transform"))
                    withShaderDefine("SUPPORT_SSBO")
                    withShaderDefine("COMPUTE_SHADER")
                    withStorageBuffer("SourceVertexData")
                    withStorageBuffer("TargetVertexData")
                    if (pipelineInfo.irisVertexFormat) {
                        withShaderDefine("IRIS_VERTEX_FORMAT")
                    }
                    if (pipelineInfo.morphed) {
                        withShaderDefine("MORPHED")
                        withShaderDefine("MAX_ENABLED_MORPH_TARGETS", BlazeRod.MAX_ENABLED_MORPH_TARGETS)
                        withUniform("MorphData", UniformType.UNIFORM_BUFFER)
                        withStorageBuffer("MorphPositionBlock")
                        withStorageBuffer("MorphColorBlock")
                        withStorageBuffer("MorphTexCoordBlock")
                        withStorageBuffer("MorphTargetIndicesData")
                        withStorageBuffer("MorphWeightsData")
                    }
                    if (pipelineInfo.skinned) {
                        withShaderDefine("SKINNED")
                        withUniform("SkinModelIndices", UniformType.UNIFORM_BUFFER)
                        withStorageBuffer("JointsData")
                    }
                    withUniform("ComputeData", UniformType.UNIFORM_BUFFER)
                    withShaderDefine("INSTANCE_SIZE", BlazeRod.INSTANCE_SIZE)
                    withShaderDefine("COMPUTE_LOCAL_SIZE", BlazeRod.COMPUTE_LOCAL_SIZE)
                    withShaderDefine("INPUT_MATERIAL", material.descriptor.id)
                }.build()
            }
        }
    }

    override val type: Type
        get() = Type

    private val dataPool = GpuShaderDataPool.ofSsbo()
    private val vertexDataPool = GpuShaderDataPool.create(
        usage = GpuBuffer.USAGE_VERTEX,
        extraUsage = GpuBufferExt.EXTRA_USAGE_STORAGE_BUFFER,
        alignment = RenderSystem.getDevice().ssboOffsetAlignment,
        supportSlicing = false,
    )

    private fun dispatchCompute(
        primitive: RenderPrimitive,
        task: RenderTask,
        skinBuffer: RenderSkinBuffer?,
        targetBuffer: MorphTargetBuffer?,
        targetVertexFormat: VertexFormat,
        irisVertexFormat: Boolean,
    ): GpuBufferSlice {
        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()
        val material = primitive.material
        var computePass: ComputePass? = null
        var targetVertexData: GpuBufferSlice
        val computeDataUniformBufferSlice: GpuBufferSlice
        var skinModelIndicesBufferSlice: GpuBufferSlice? = null
        var skinJointBufferSlice: GpuBufferSlice? = null
        var morphDataUniformBufferSlice: GpuBufferSlice? = null
        var morphWeightsBufferSlice: GpuBufferSlice? = null
        var morphTargetIndicesBufferSlice: GpuBufferSlice? = null

        try {
            targetVertexData = vertexDataPool.allocate(targetVertexFormat.vertexSize * primitive.vertices)
            computeDataUniformBufferSlice = ComputeDataUniformBuffer.write {
                totalVertices = primitive.vertices.toUInt()
                uv1 = OverlayTexture.DEFAULT_UV.toUInt()
                uv2 = task.light.toUInt()
            }
            skinBuffer?.let { skinBuffer ->
                skinModelIndicesBufferSlice = SkinModelIndicesUniformBuffer.write {
                    skinJoints = skinBuffer.jointSize
                }
                skinJointBufferSlice = dataPool.upload(skinBuffer.buffer)
            }
            targetBuffer?.let { targetBuffer ->
                primitive.targets?.let { targets ->
                    morphDataUniformBufferSlice = MorphDataUniformBuffer.write {
                        totalVertices = primitive.vertices
                        posTargets = targets.position.targetsCount
                        colorTargets = targets.color.targetsCount
                        texCoordTargets = targets.texCoord.targetsCount
                        totalTargets =
                            targets.position.targetsCount + targets.color.targetsCount + targets.texCoord.targetsCount
                    }
                }
                morphWeightsBufferSlice = dataPool.upload(targetBuffer.weightsBuffer)
                morphTargetIndicesBufferSlice = dataPool.upload(targetBuffer.indicesBuffer)
            }

            val pipeline = getPipeline(
                material = material,
                irisVertexFormat = irisVertexFormat,
            )

            computePass = commandEncoder.createComputePass { "BlazeRod compute pass" }

            with(computePass) {
                setPipeline(pipeline)

                if (RenderPassImpl.IS_DEVELOPMENT) {
                    require(material.skinned == (skinBuffer != null)) {
                        "Primitive's skin data ${skinBuffer != null} and material skinned ${material.skinned} not matching"
                    }
                }
                setStorageBuffer("SourceVertexData", primitive.gpuVertexBuffer!!.inner.slice())
                setStorageBuffer("TargetVertexData", targetVertexData)
                setUniform("ComputeData", computeDataUniformBufferSlice)
                skinJointBufferSlice?.let { skinJointBuffer ->
                    if (device.supportSsbo) {
                        setStorageBuffer("JointsData", skinJointBuffer)
                    } else {
                        setUniform("Joints", skinJointBuffer)
                    }
                }
                skinModelIndicesBufferSlice?.let { skinModelIndices ->
                    setUniform("SkinModelIndices", skinModelIndices)
                }
                morphDataUniformBufferSlice?.let { morphDataUniformBuffer ->
                    setUniform("MorphData", morphDataUniformBuffer)
                }
                morphWeightsBufferSlice?.let { morphWeightsBuffer ->
                    if (device.supportSsbo) {
                        setStorageBuffer("MorphWeightsData", morphWeightsBuffer)
                    } else {
                        setUniform("MorphWeights", morphWeightsBuffer)
                    }
                }
                morphTargetIndicesBufferSlice?.let { morphTargetIndicesBuffer ->
                    if (device.supportSsbo) {
                        setStorageBuffer("MorphTargetIndicesData", morphTargetIndicesBuffer)
                    } else {
                        setUniform("MorphTargetIndices", morphTargetIndicesBuffer)
                    }
                }
                primitive.targets?.let { targets ->
                    setStorageBuffer("MorphPositionBlock", targets.position.slice!!)
                    setStorageBuffer("MorphColorBlock", targets.color.slice!!)
                    setStorageBuffer("MorphTexCoordBlock", targets.texCoord.slice!!)
                }
                val totalWorkSize = primitive.vertices ceilDiv BlazeRod.COMPUTE_LOCAL_SIZE
                computePass.dispatch(totalWorkSize, 1, 1)
            }
        } finally {
            computePass?.close()
        }

        return targetVertexData
    }

    private class ComputeItem private constructor() {
        private var released = true
        private var _primitiveComponent: PrimitiveNodeComponent? = null
        private var _renderTask: RenderTask? = null
        private var _vertexFormat: VertexFormat? = null
        private var _vertexBuffer: GpuBufferSlice? = null

        val primitiveComponent
            get() = _primitiveComponent!!
        val renderTask
            get() = _renderTask!!
        val vertexFormat
            get() = _vertexFormat!!
        val vertexBuffer
            get() = _vertexBuffer!!

        fun release() {
            if (released) {
                return
            }
            POOL.release(this)
        }

        companion object {
            private val POOL = ObjectPool(
                identifier = Identifier.of("blazerod", "compute_item"),
                create = ::ComputeItem,
                onAcquired = {
                    released = false
                },
                onReleased = {
                    released = true
                    _primitiveComponent = null
                    _renderTask = null
                    _vertexFormat = null
                    _vertexBuffer = null
                },
                onClosed = {},
            )

            fun acquire(
                primitiveComponent: PrimitiveNodeComponent,
                renderTask: RenderTask,
                vertexFormat: VertexFormat,
                vertexBuffer: GpuBufferSlice,
            ) = POOL.acquire().apply {
                _primitiveComponent = primitiveComponent
                _renderTask = renderTask
                _vertexFormat = vertexFormat
                _vertexBuffer = vertexBuffer
            }
        }
    }

    private val renderTasks = mutableListOf<RenderTask>()
    private val computeItems = mutableListOf<ComputeItem>()

    override fun schedule(task: RenderTask) {
        val instance = task.instance
        val scene = instance.scene
        renderTasks.add(task)
        for (primitiveComponent in scene.primitiveComponents) {
            val primitive = primitiveComponent.primitive

            if (!primitive.gpuComplete) {
                return
            }

            val irisVertexFormat = IrisApiWrapper.shaderPackInUse
            val targetVertexFormat = if (irisVertexFormat) {
                BlazerodVertexFormats.IRIS_ENTITY_PADDED
            } else {
                BlazerodVertexFormats.ENTITY_PADDED
            }
            val vertexBuffer = dispatchCompute(
                primitive = primitive,
                task = task,
                skinBuffer = primitiveComponent.skinIndex?.let { task.skinBuffer[it] }?.content,
                targetBuffer = primitiveComponent.morphedPrimitiveIndex?.let { task.morphTargetBuffer[it] }?.content,
                targetVertexFormat = targetVertexFormat,
                irisVertexFormat = irisVertexFormat,
            )

            val item = ComputeItem.acquire(
                primitiveComponent = primitiveComponent,
                renderTask = task,
                vertexFormat = targetVertexFormat,
                vertexBuffer = vertexBuffer,
            )

            computeItems.add(item)
        }
    }

    private val modelMatrix = Matrix4f()
    private val baseColor = Vector4f()
    override fun executeTasks(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
    ) {
        if (computeItems.isEmpty()) {
            return
        }

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()
        commandEncoder.memoryBarrier(CommandEncoderExt.BARRIER_STORAGE_BUFFER_BIT or CommandEncoderExt.BARRIER_VERTEX_BUFFER_BIT)

        for (item in computeItems) {
            val task = item.renderTask
            val primitiveComponent = item.primitiveComponent
            val primitive = primitiveComponent.primitive
            val instance = task.instance
            val material = primitive.material

            instance.modelData.modelMatricesBuffer.content.getMatrix(primitiveComponent.primitiveIndex, modelMatrix)
            modelMatrix.mulLocal(task.modelViewMatrix)
            val dynamicUniforms = RenderSystem.getDynamicUniforms().write(
                modelMatrix,
                material.baseColor.toVector4f(baseColor),
                RenderSystem.getModelOffset(),
                RenderSystem.getTextureMatrix(),
                RenderSystem.getShaderLineWidth()
            )

            commandEncoder.createRenderPass(
                { "BlazeRod render pass" },
                colorFrameBuffer,
                OptionalInt.empty(),
                depthFrameBuffer,
                OptionalDouble.empty()
            ).use {
                with(it) {
                    setPipeline(RenderPipelines.ENTITY_TRANSLUCENT)
                    RenderSystem.bindDefaultUniforms(this)
                    setUniform("DynamicTransforms", dynamicUniforms)
                    bindSampler(
                        "Sampler2",
                        MinecraftClient.getInstance().gameRenderer.lightmapTextureManager.glTextureView
                    )
                    bindSampler(
                        "Sampler1",
                        MinecraftClient.getInstance().gameRenderer.overlayTexture.texture.glTextureView
                    )
                    (material as? RenderMaterial.Unlit)?.let { material ->
                        bindSampler("Sampler0", material.baseColorTexture.view)
                    }

                    setVertexFormat(item.vertexFormat)
                    setVertexFormatMode(primitive.vertexFormatMode)
                    setVertexBuffer(0, item.vertexBuffer.buffer())
                    primitive.indexBuffer?.let { indices ->
                        setIndexBuffer(indices)
                        drawIndexed(0, 0, indices.length, 1)
                    } ?: run {
                        draw(0, primitive.vertices)
                    }
                }
            }
            item.release()
        }
        computeItems.clear()
        renderTasks.forEach { it.release() }
        renderTasks.clear()
    }

    override fun render(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
        scene: RenderScene,
        primitive: RenderPrimitive,
        primitiveIndex: Int,
        task: RenderTask,
        skinBuffer: RenderSkinBuffer?,
        targetBuffer: MorphTargetBuffer?,
    ) {
        if (!primitive.gpuComplete) {
            return
        }

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()
        val instance = task.instance
        val material = primitive.material

        val irisVertexFormat = IrisApiWrapper.shaderPackInUse
        val targetVertexFormat = if (irisVertexFormat) {
            BlazerodVertexFormats.IRIS_ENTITY_PADDED
        } else {
            BlazerodVertexFormats.ENTITY_PADDED
        }
        val vertexBuffer = dispatchCompute(
            primitive = primitive,
            task = task,
            skinBuffer = skinBuffer,
            targetBuffer = targetBuffer,
            targetVertexFormat = targetVertexFormat,
            irisVertexFormat = irisVertexFormat,
        )

        commandEncoder.memoryBarrier(CommandEncoderExt.BARRIER_STORAGE_BUFFER_BIT or CommandEncoderExt.BARRIER_VERTEX_BUFFER_BIT)

        instance.modelData.modelMatricesBuffer.content.getMatrix(primitiveIndex, modelMatrix)
        modelMatrix.mulLocal(task.modelViewMatrix)
        val dynamicUniforms = RenderSystem.getDynamicUniforms().write(
            modelMatrix,
            material.baseColor.toVector4f(baseColor),
            RenderSystem.getModelOffset(),
            RenderSystem.getTextureMatrix(),
            RenderSystem.getShaderLineWidth()
        )

        commandEncoder.createRenderPass(
            { "BlazeRod render pass" },
            colorFrameBuffer,
            OptionalInt.empty(),
            depthFrameBuffer,
            OptionalDouble.empty()
        ).use {
            with(it) {
                setPipeline(RenderPipelines.ENTITY_TRANSLUCENT)
                RenderSystem.bindDefaultUniforms(this)
                setUniform("DynamicTransforms", dynamicUniforms)
                bindSampler("Sampler2", MinecraftClient.getInstance().gameRenderer.lightmapTextureManager.glTextureView)
                bindSampler("Sampler1", MinecraftClient.getInstance().gameRenderer.overlayTexture.texture.glTextureView)
                (material as? RenderMaterial.Unlit)?.let { material ->
                    bindSampler("Sampler0", material.baseColorTexture.view)
                }

                setVertexFormat(targetVertexFormat)
                setVertexFormatMode(primitive.vertexFormatMode)
                setVertexBuffer(0, vertexBuffer.buffer())
                primitive.indexBuffer?.let { indices ->
                    setIndexBuffer(indices)
                    drawIndexed(0, 0, indices.length, 1)
                } ?: run {
                    draw(0, primitive.vertices)
                }
            }
        }
    }

    override fun rotate() {
        computeItems.forEach { it.release() }
        computeItems.clear()
        renderTasks.forEach { it.release() }
        renderTasks.clear()
        dataPool.rotate()
        vertexDataPool.rotate()
    }

    override fun close() {
        computeItems.forEach { it.release() }
        computeItems.clear()
        renderTasks.forEach { it.release() }
        renderTasks.clear()
        dataPool.close()
        vertexDataPool.close()
    }
}