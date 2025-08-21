package top.fifthlight.blazerod.model.load

import com.mojang.blaze3d.vertex.VertexFormat
import kotlinx.coroutines.Deferred
import net.minecraft.client.texture.NativeImage
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.resource.*
import top.fifthlight.blazerod.render.BlazerodVertexFormats
import top.fifthlight.blazerod.render.GpuIndexBuffer
import top.fifthlight.blazerod.render.RefCountedGpuBuffer
import java.nio.ByteBuffer
import top.fifthlight.blazerod.model.Camera as ModelCamera
import top.fifthlight.blazerod.model.IkTarget as ModelIkTarget
import top.fifthlight.blazerod.model.RigidBody as ModelRigidBody

data class TextureLoadData(
    val name: String?,
    val nativeImage: NativeImage,
    val sampler: Texture.Sampler,
) : AutoCloseable by nativeImage

data class IndexBufferLoadData(
    val type: VertexFormat.IndexType,
    val length: Int,
    val buffer: ByteBuffer,
)

sealed class MaterialLoadInfo {
    abstract val name: String?
    abstract val baseColor: RgbaColor
    abstract val baseColorTexture: TextureInfo?
    abstract val alphaMode: Material.AlphaMode
    abstract val alphaCutoff: Float
    abstract val doubleSided: Boolean
    abstract val skinned: Boolean
    abstract val morphed: Boolean

    open fun getVertexFormat(skinned: Boolean): VertexFormat = if (skinned) {
        BlazerodVertexFormats.POSITION_COLOR_TEXTURE_JOINT_WEIGHT
    } else {
        BlazerodVertexFormats.POSITION_COLOR_TEXTURE
    }

    data class TextureInfo(
        val textureIndex: Int,
        val textureCoordinate: Int,
    )

    data class Pbr(
        override val name: String?,
        override val baseColor: RgbaColor,
        override val baseColorTexture: TextureInfo?,
        val metallicFactor: Float,
        val roughnessFactor: Float,
        val metallicRoughnessTexture: TextureInfo?,
        val normalTexture: TextureInfo?,
        val occlusionTexture: TextureInfo?,
        val emissiveTexture: TextureInfo?,
        val emissiveFactor: RgbColor,
        override val alphaMode: Material.AlphaMode,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
        override val skinned: Boolean,
        override val morphed: Boolean,
    ) : MaterialLoadInfo()

    data class Unlit(
        override val name: String?,
        override val baseColor: RgbaColor,
        override val baseColorTexture: TextureInfo?,
        override val alphaMode: Material.AlphaMode,
        override val alphaCutoff: Float,
        override val doubleSided: Boolean,
        override val skinned: Boolean,
        override val morphed: Boolean,
    ) : MaterialLoadInfo()
}

data class MorphTargetsLoadData<Info : Any>(
    val targetGroups: List<MorphTargetGroup>,
    val position: Info,
    val color: Info,
    val texCoord: Info,
) {
    data class TargetInfo(
        val buffer: ByteBuffer,
        val itemStride: Int,
        val targetsCount: Int,
    )
}

data class PrimitiveLoadInfo(
    val vertices: Int,
    val vertexFormatMode: VertexFormat.DrawMode,
    val materialInfo: MaterialLoadInfo?,
    val indexBufferIndex: Int?,
    val vertexBufferIndex: Int,
    val skinIndex: Int?,
    val morphedPrimitiveIndex: Int?,
)

data class NodeLoadInfo(
    val nodeId: NodeId?,
    val nodeName: String?,
    val humanoidTags: List<HumanoidTag>,
    val transform: NodeTransform?,
    val components: List<Component>,
    val childrenIndices: List<Int>,
) {
    sealed class Component {
        data class Primitive(
            val infoIndex: Int,
        ) : Component()

        data class Joint(
            val skinIndex: Int,
            val jointIndex: Int,
        ) : Component()

        data class Camera(
            val camera: ModelCamera,
        ) : Component()

        data class IkTarget(
            val ikIndex: Int,
            val ikTarget: ModelIkTarget,
            val transformId: TransformId,
        ) : Component()

        data class InfluenceSource(
            val influence: Influence,
            val transformId: TransformId,
        ) : Component()

        data class RigidBody(
            val rigidBodyIndex: Int,
            val rigidBody: ModelRigidBody,
        ) : Component()
    }
}

data class GpuLoadVertexData(
    val gpuBuffer: RefCountedGpuBuffer?,
    val cpuBuffer: ByteBuffer?,
)

data class ModelLoadInfo<Texture : Any?, Index : Any, Vertex : Any, Morph : Any>(
    val textures: List<Deferred<Texture>>,
    val indexBuffers: List<Deferred<Index>>,
    val vertexBuffers: List<Deferred<Vertex>>,
    val primitiveInfos: List<PrimitiveLoadInfo>,
    val morphTargetInfos: List<Deferred<Morph>>,
    val nodes: List<NodeLoadInfo>,
    val rootNodeIndex: Int,
    val skins: List<RenderSkin>,
    val expressions: List<RenderExpression>,
    val expressionGroups: List<RenderExpressionGroup>,
)

typealias PreProcessModelLoadInfo = ModelLoadInfo<TextureLoadData?, IndexBufferLoadData, ByteBuffer, MorphTargetsLoadData<MorphTargetsLoadData.TargetInfo>>
typealias GpuLoadModelLoadInfo = ModelLoadInfo<RenderTexture?, GpuIndexBuffer, GpuLoadVertexData, MorphTargetsLoadData<RenderPrimitive.Target>>
