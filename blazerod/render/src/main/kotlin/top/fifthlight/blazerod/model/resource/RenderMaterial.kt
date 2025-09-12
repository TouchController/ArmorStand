package top.fifthlight.blazerod.model.resource

import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.util.Identifier
import top.fifthlight.blazerod.model.Material.AlphaMode
import top.fifthlight.blazerod.model.Material.AlphaMode.OPAQUE
import top.fifthlight.blazerod.model.RgbColor
import top.fifthlight.blazerod.model.RgbaColor
import top.fifthlight.blazerod.render.BlazerodVertexFormats
import top.fifthlight.blazerod.util.AbstractRefCount

sealed class RenderMaterial<Desc : RenderMaterial.Descriptor> : AbstractRefCount() {
    companion object {
        val defaultMaterial by lazy {
            // Increase reference count to avoid being closed
            Unlit(name = "Default").apply { increaseReferenceCount() }
        }
    }

    abstract val name: String?
    abstract val baseColor: RgbaColor
    abstract val baseColorTexture: RenderTexture?
    abstract val alphaMode: AlphaMode
    abstract val alphaCutoff: Float
    abstract val doubleSided: Boolean
    abstract val skinned: Boolean
    abstract val morphed: Boolean

    abstract val descriptor: Desc

    abstract class Descriptor(
        val id: Int,
        val name: String,
    ) {
        val typeId: Identifier = Identifier.of("blazerod", "material_$name")
    }

    abstract val vertexFormat: VertexFormat

    override val typeId: Identifier
        get() = descriptor.typeId

    abstract override fun onClosed()

    class Pbr(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        val metallicFactor: Float = 1f,
        val roughnessFactor: Float = 1f,
        val metallicRoughnessTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        val normalTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        val occlusionTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        val emissiveTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        val emissiveFactor: RgbColor = RgbColor(1f, 1f, 1f),
        override val alphaMode: AlphaMode = OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean,
        override val skinned: Boolean,
        override val morphed: Boolean,
    ) : RenderMaterial<Pbr.Descriptor>() {
        init {
            baseColorTexture.increaseReferenceCount()
            metallicRoughnessTexture.increaseReferenceCount()
            normalTexture.increaseReferenceCount()
            occlusionTexture.increaseReferenceCount()
            emissiveTexture.increaseReferenceCount()
        }

        override val descriptor
            get() = Descriptor

        override val vertexFormat: VertexFormat
            get() = TODO("Not yet implemented")

        override fun onClosed() {
            baseColorTexture.decreaseReferenceCount()
            metallicRoughnessTexture.decreaseReferenceCount()
            normalTexture.decreaseReferenceCount()
            occlusionTexture.decreaseReferenceCount()
            emissiveTexture.decreaseReferenceCount()
        }

        companion object Descriptor : RenderMaterial.Descriptor(
            id = 1,
            name = "pbr",
        )
    }

    class Unlit(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        override val alphaMode: AlphaMode = OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
        override val skinned: Boolean = false,
        override val morphed: Boolean = false,
    ) : RenderMaterial<Unlit.Descriptor>() {
        init {
            baseColorTexture.increaseReferenceCount()
        }

        override val descriptor
            get() = Descriptor

        override val vertexFormat: VertexFormat
            get() = if (skinned) {
                BlazerodVertexFormats.POSITION_COLOR_TEXTURE_JOINT_WEIGHT
            } else {
                BlazerodVertexFormats.POSITION_COLOR_TEXTURE
            }

        override fun onClosed() {
            baseColorTexture.decreaseReferenceCount()
        }

        companion object Descriptor : RenderMaterial.Descriptor(
            id = 0,
            name = "unlit",
        )
    }


    class Vanilla(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: RenderTexture = RenderTexture.WHITE_RGBA_TEXTURE,
        override val alphaMode: AlphaMode = OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
        override val skinned: Boolean = false,
        override val morphed: Boolean = false,
    ) : RenderMaterial<Vanilla.Descriptor>() {
        init {
            baseColorTexture.increaseReferenceCount()
        }

        override val descriptor
            get() = Descriptor

        override val vertexFormat: VertexFormat
            get() = if (skinned) {
                BlazerodVertexFormats.POSITION_COLOR_TEXTURE_NORMAL_JOINT_WEIGHT
            } else {
                BlazerodVertexFormats.POSITION_COLOR_TEXTURE_NORMAL
            }

        override fun onClosed() {
            baseColorTexture.decreaseReferenceCount()
        }

        companion object Descriptor : RenderMaterial.Descriptor(
            id = 2,
            name = "vanilla",
        )
    }
}
