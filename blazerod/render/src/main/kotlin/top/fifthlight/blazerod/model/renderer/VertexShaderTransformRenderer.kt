package top.fifthlight.blazerod.model.renderer

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPassImpl
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.util.Identifier
import org.joml.Vector2i
import top.fifthlight.blazerod.BlazeRod
import top.fifthlight.blazerod.extension.*
import top.fifthlight.blazerod.model.RenderScene
import top.fifthlight.blazerod.model.RenderTask
import top.fifthlight.blazerod.model.data.MorphTargetBuffer
import top.fifthlight.blazerod.model.data.RenderSkinBuffer
import top.fifthlight.blazerod.model.node.component.PrimitiveNodeComponent
import top.fifthlight.blazerod.model.resource.RenderMaterial
import top.fifthlight.blazerod.model.resource.RenderPrimitive
import top.fifthlight.blazerod.model.uniform.InstanceDataUniformBuffer
import top.fifthlight.blazerod.model.uniform.MorphDataUniformBuffer
import top.fifthlight.blazerod.model.uniform.SkinModelIndicesUniformBuffer
import top.fifthlight.blazerod.model.uniform.UnlitDataUniformBuffer
import top.fifthlight.blazerod.render.BlazerodVertexFormats
import top.fifthlight.blazerod.render.setIndexBuffer
import top.fifthlight.blazerod.util.*
import java.util.*

class VertexShaderTransformRenderer private constructor() :
    TaskMapScheduledRenderer<VertexShaderTransformRenderer, VertexShaderTransformRenderer.Type>() {

    @Suppress("NOTHING_TO_INLINE")
    @JvmInline
    private value class PipelineInfo(val bitmap: BitmapItem = BitmapItem()) {
        constructor(
            doubleSided: Boolean = true,
            skinned: Boolean = false,
            instanced: Boolean = false,
            morphed: Boolean = false,
        ) : this(Unit.run {
            var item = BitmapItem()
            if (doubleSided) {
                item += ELEMENT_DOUBLE_SIDED
            }
            if (skinned) {
                item += ELEMENT_SKINNED
            }
            if (instanced) {
                item += ELEMENT_INSTANCED
            }
            if (morphed) {
                item += ELEMENT_MORPHED
            }
            item
        })

        constructor(material: RenderMaterial<*>, instanced: Boolean) : this(
            doubleSided = material.doubleSided,
            skinned = material.skinned,
            instanced = instanced,
            morphed = material.morphed
        )

        val doubleSided
            get() = ELEMENT_DOUBLE_SIDED in bitmap
        val skinned
            get() = ELEMENT_SKINNED in bitmap
        val instanced
            get() = ELEMENT_INSTANCED in bitmap
        val morphed
            get() = ELEMENT_MORPHED in bitmap

        fun nameSuffix() = buildString {
            if (doubleSided) {
                append("_cull")
            } else {
                append("_no_cull")
            }
            if (skinned) {
                append("_skinned")
            }
            if (instanced) {
                append("_instanced")
            }
            if (morphed) {
                append("_morphed")
            }
        }

        companion object {
            val ELEMENT_DOUBLE_SIDED = BitmapItem.Element.of(0)
            val ELEMENT_SKINNED = BitmapItem.Element.of(1)
            val ELEMENT_INSTANCED = BitmapItem.Element.of(2)
            val ELEMENT_MORPHED = BitmapItem.Element.of(3)
        }

        inline operator fun plus(element: BitmapItem.Element) =
            PipelineInfo(bitmap + element)

        inline operator fun minus(element: BitmapItem.Element) =
            PipelineInfo(bitmap - element)

        inline operator fun contains(element: BitmapItem.Element) =
            element in bitmap

        override fun toString(): String {
            return "PipelineInfo(doubleSided=$doubleSided, skinned=$skinned, instanced=$instanced, morphed=$morphed)"
        }
    }

    companion object Type : Renderer.Type<VertexShaderTransformRenderer, Type>() {
        override val isAvailable: Boolean
            get() = true
        override val supportScheduling: Boolean
            get() = true

        @JvmStatic
        override fun create() = VertexShaderTransformRenderer()

        private val useSsbo by lazy {
            val device = RenderSystem.getDevice()
            device.supportSsbo && device.maxSsboInVertexShader >= 8
        }

        private val pipelineCache = mutableMapOf<RenderMaterial.Descriptor, Int2ReferenceMap<RenderPipeline>>()

        private fun getPipeline(material: RenderMaterial<*>, instanced: Boolean): RenderPipeline {
            val pipelineInfo = PipelineInfo(
                material = material,
                instanced = instanced,
            )
            val materialMap = pipelineCache.getOrPut(material.descriptor) { Int2ReferenceAVLTreeMap() }
            return materialMap.getOrPut(pipelineInfo.bitmap.inner) {
                RenderPipeline.builder().apply {
                    withCull(!pipelineInfo.doubleSided)
                    withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    val useSsbo = RenderSystem.getDevice().supportSsbo
                    if (useSsbo) {
                        withShaderDefine("SUPPORT_SSBO")
                    }
                    if (pipelineInfo.morphed) {
                        withShaderDefine("MORPHED")
                        withShaderDefine("MAX_ENABLED_MORPH_TARGETS", BlazeRod.MAX_ENABLED_MORPH_TARGETS)
                        withUniform("MorphData", UniformType.UNIFORM_BUFFER)
                        if (useSsbo) {
                            withStorageBuffer("MorphPositionBlock")
                            withStorageBuffer("MorphColorBlock")
                            withStorageBuffer("MorphTexCoordBlock")
                            withStorageBuffer("MorphTargetIndicesData")
                            withStorageBuffer("MorphWeightsData")
                        } else {
                            withUniform("MorphPositionData", UniformType.TEXEL_BUFFER, TextureFormatExt.RGBA32F)
                            withUniform("MorphColorData", UniformType.TEXEL_BUFFER, TextureFormatExt.RGBA32F)
                            withUniform("MorphTexCoordData", UniformType.TEXEL_BUFFER, TextureFormatExt.RG32F)
                            withUniform("MorphTargetIndices", UniformType.TEXEL_BUFFER, TextureFormatExt.R32I)
                            withUniform("MorphWeights", UniformType.TEXEL_BUFFER, TextureFormatExt.R32F)
                        }
                    }
                    if (pipelineInfo.skinned) {
                        withShaderDefine("SKINNED")
                        withUniform("SkinModelIndices", UniformType.UNIFORM_BUFFER)
                        if (useSsbo) {
                            withStorageBuffer("JointsData")
                        } else {
                            withUniform("Joints", UniformType.TEXEL_BUFFER, TextureFormatExt.RGBA32F)
                        }
                    }
                    withUniform("InstanceData", UniformType.UNIFORM_BUFFER)
                    if (useSsbo) {
                        withStorageBuffer("LocalMatricesData")
                    } else {
                        withUniform("LocalMatrices", UniformType.TEXEL_BUFFER, TextureFormatExt.RGBA32F)
                    }
                    withShaderDefine("INSTANCE_SIZE", BlazeRod.INSTANCE_SIZE)
                    if (instanced) {
                        withShaderDefine("INSTANCED")
                    }

                    when (material) {
                        is RenderMaterial.Pbr -> TODO("PBR is not support for now")
                        is RenderMaterial.Unlit -> {
                            withLocation(Identifier.of("blazerod", "unlit" + pipelineInfo.nameSuffix()))

                            withVertexShader(Identifier.of("blazerod", "core/unlit"))
                            withFragmentShader(Identifier.of("blazerod", "core/unlit"))
                            withBlend(BlendFunction.TRANSLUCENT)
                            withSampler("SamplerBaseColor")
                            withSampler("SamplerLightMap")
                            if (pipelineInfo.skinned) {
                                withVertexFormat(BlazerodVertexFormats.POSITION_COLOR_TEXTURE_JOINT_WEIGHT)
                            } else {
                                withVertexFormat(BlazerodVertexFormats.POSITION_COLOR_TEXTURE)
                            }

                            withUniform("UnlitData", UniformType.UNIFORM_BUFFER)
                        }
                    }
                }.build()
            }
        }
    }

    override val type: Type
        get() = Type

    private val dataPool = if (useSsbo) {
        GpuShaderDataPool.ofSsbo()
    } else {
        GpuShaderDataPool.ofTbo()
    }

    private val lightVector = Vector2i()

    private fun RenderPass.bindMorphTargets(targets: RenderPrimitive.Targets) {
        if (RenderSystem.getDevice().supportSsbo) {
            setStorageBuffer("MorphPositionBlock", targets.position.slice!!)
            setStorageBuffer("MorphColorBlock", targets.color.slice!!)
            setStorageBuffer("MorphTexCoordBlock", targets.texCoord.slice!!)
        } else {
            setUniform("MorphPositionData", targets.position.gpuBuffer)
            setUniform("MorphColorData", targets.color.gpuBuffer)
            setUniform("MorphTexCoordData", targets.texCoord.gpuBuffer)
        }
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
        val material = primitive.material
        var renderPass: RenderPass? = null
        val instanceDataUniformBufferSlice: GpuBufferSlice
        val modelMatricesBufferSlice: GpuBufferSlice
        var skinModelIndicesBufferSlice: GpuBufferSlice? = null
        var skinJointBufferSlice: GpuBufferSlice? = null
        var morphDataUniformBufferSlice: GpuBufferSlice? = null
        var morphWeightsBufferSlice: GpuBufferSlice? = null
        var morphTargetIndicesBufferSlice: GpuBufferSlice? = null
        var unlitData: GpuBufferSlice? = null

        try {
            instanceDataUniformBufferSlice = InstanceDataUniformBuffer.write {
                primitiveSize = scene.primitiveComponents.size
                this.primitiveIndex = primitiveIndex
                this.modelViewMatrices[0] = task.modelViewMatrix
                lightVector.set(
                    task.light and (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE or 0xFF0F),
                    (task.light shr 16) and (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE or 0xFF0F)
                )
                this.lightMapUvs[0] = lightVector
            }
            modelMatricesBufferSlice = dataPool.upload(task.modelMatricesBuffer.content.buffer)
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
            (material as? RenderMaterial.Unlit)?.let { material ->
                unlitData = UnlitDataUniformBuffer.write {
                    baseColor = material.baseColor
                }
            }

            val pipeline = getPipeline(material = material, instanced = false)

            renderPass = commandEncoder.createRenderPass(
                { "BlazeRod render pass (non-instanced)" },
                colorFrameBuffer,
                OptionalInt.empty(),
                depthFrameBuffer,
                OptionalDouble.empty()
            )

            with(renderPass) {
                setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(this)
                unlitData?.let {
                    setUniform("UnlitData", unlitData)
                }
                (material as? RenderMaterial.Unlit)?.let { material ->
                    bindSampler("SamplerBaseColor", material.baseColorTexture.view)
                    val lightMapTexture =
                        MinecraftClient.getInstance().gameRenderer.lightmapTextureManager.glTextureView
                    bindSampler("SamplerLightMap", lightMapTexture)
                }

                if (RenderPassImpl.IS_DEVELOPMENT) {
                    require(material.skinned == (skinBuffer != null)) {
                        "Primitive's skin data ${skinBuffer != null} and material skinned ${material.skinned} not matching"
                    }
                }
                setUniform("InstanceData", instanceDataUniformBufferSlice)
                if (device.supportSsbo) {
                    setStorageBuffer("LocalMatricesData", modelMatricesBufferSlice)
                } else {
                    setUniform("LocalMatrices", modelMatricesBufferSlice)
                }
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
                setVertexFormatMode(primitive.vertexFormatMode)
                setVertexBuffer(0, primitive.gpuVertexBuffer!!.inner)
                primitive.targets?.let { targets ->
                    bindMorphTargets(targets)
                }
                primitive.indexBuffer?.let { indices ->
                    setIndexBuffer(indices)
                    drawIndexed(0, 0, indices.length, 1)
                } ?: run {
                    draw(0, primitive.vertices)
                }
            }
        } finally {
            renderPass?.close()
        }
    }

    override fun renderInstanced(
        colorFrameBuffer: GpuTextureView,
        depthFrameBuffer: GpuTextureView?,
        tasks: List<RenderTask>,
        scene: RenderScene,
        component: PrimitiveNodeComponent,
    ) {
        val primitive = component.primitive
        val material = primitive.material
        if (!primitive.gpuComplete) {
            return
        }

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()
        var renderPass: RenderPass? = null
        val instanceDataUniformBufferSlice: GpuBufferSlice
        val modelMatricesBufferSlice: GpuBufferSlice
        var skinModelIndicesBufferSlice: GpuBufferSlice? = null
        var skinJointBufferSlice: GpuBufferSlice? = null
        var morphDataUniformBufferSlice: GpuBufferSlice? = null
        var morphWeightsBufferSlice: GpuBufferSlice? = null
        var morphTargetIndicesBufferSlice: GpuBufferSlice? = null
        var unlitData: GpuBufferSlice? = null

        val firstTask = tasks.first()
        try {
            instanceDataUniformBufferSlice = InstanceDataUniformBuffer.write {
                primitiveSize = scene.primitiveComponents.size
                this.primitiveIndex = component.primitiveIndex
                for ((index, task) in tasks.withIndex()) {
                    val light = task.light
                    lightVector.set(
                        light and (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE or 0xFF0F),
                        (light shr 16) and (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE or 0xFF0F)
                    )
                    this.lightMapUvs[index] = lightVector
                    this.modelViewMatrices[index] = task.modelViewMatrix
                }
            }
            modelMatricesBufferSlice = dataPool.upload(tasks.map { it.modelMatricesBuffer.content.buffer })
            component.skinIndex?.let { skinIndex ->
                val firstSkinBuffer = firstTask.skinBuffer[skinIndex].content
                skinModelIndicesBufferSlice = SkinModelIndicesUniformBuffer.write {
                    skinJoints = firstSkinBuffer.jointSize
                }
                skinJointBufferSlice =
                    dataPool.upload(tasks.map { it.skinBuffer[skinIndex].content.buffer })
            }
            component.morphedPrimitiveIndex?.let { morphedPrimitiveIndex ->
                val targets = primitive.targets ?: error("Morphed primitive index was set but targets were not")
                morphDataUniformBufferSlice = MorphDataUniformBuffer.write {
                    totalVertices = primitive.vertices
                    posTargets = targets.position.targetsCount
                    colorTargets = targets.color.targetsCount
                    texCoordTargets = targets.texCoord.targetsCount
                    totalTargets =
                        targets.position.targetsCount + targets.color.targetsCount + targets.texCoord.targetsCount
                }
                morphWeightsBufferSlice =
                    dataPool.upload(tasks.map { it.morphTargetBuffer[morphedPrimitiveIndex].content.weightsBuffer })
                morphTargetIndicesBufferSlice =
                    dataPool.upload(tasks.map { it.morphTargetBuffer[morphedPrimitiveIndex].content.indicesBuffer })
            }
            (material as? RenderMaterial.Unlit)?.let { material ->
                unlitData = UnlitDataUniformBuffer.write {
                    baseColor = material.baseColor
                }
            }

            val pipeline = getPipeline(material = material, instanced = true)

            renderPass = commandEncoder.createRenderPass(
                { "BlazeRod render pass (instanced)" },
                colorFrameBuffer,
                OptionalInt.empty(),
                depthFrameBuffer,
                OptionalDouble.empty()
            )

            with(renderPass) {
                setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(this)
                unlitData?.let {
                    setUniform("UnlitData", unlitData)
                }
                (material as? RenderMaterial.Unlit)?.let { material ->
                    bindSampler("SamplerBaseColor", material.baseColorTexture.view)
                    val lightMapTexture =
                        MinecraftClient.getInstance().gameRenderer.lightmapTextureManager.glTextureView
                    bindSampler("SamplerLightMap", lightMapTexture)
                }

                if (RenderPassImpl.IS_DEVELOPMENT) {
                    require(material.skinned == (component.skinIndex != null)) {
                        "Primitive's skin data and material skinned property not matching"
                    }
                }
                setUniform("InstanceData", instanceDataUniformBufferSlice)
                if (device.supportSsbo) {
                    setStorageBuffer("LocalMatricesData", modelMatricesBufferSlice)
                } else {
                    setUniform("LocalMatrices", modelMatricesBufferSlice)
                }
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
                setVertexFormatMode(primitive.vertexFormatMode)
                setVertexBuffer(0, primitive.gpuVertexBuffer!!.inner)
                primitive.targets?.let { targets ->
                    bindMorphTargets(targets)
                }
                primitive.indexBuffer?.let { indices ->
                    setIndexBuffer(indices)
                    drawIndexed(0, 0, indices.length, tasks.size)
                } ?: run {
                    draw(0, 0, primitive.vertices, tasks.size)
                }
            }
        } finally {
            renderPass?.close()
        }
    }

    override fun rotate() {
        dataPool.rotate()
    }

    override fun close() {
        dataPool.close()
    }
}