package top.fifthlight.blazerod.model

sealed class Material {
    abstract val name: String?
    abstract val baseColor: RgbaColor
    abstract val baseColorTexture: TextureInfo?
    abstract val alphaMode: AlphaMode
    abstract val alphaCutoff: Float
    abstract val doubleSided: Boolean

    enum class AlphaMode {
        OPAQUE,
        MASK,
        BLEND,
    }

    data class TextureInfo(
        val texture: Texture,
        val textureCoordinate: Int = 0,
    )

    data class Pbr(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: TextureInfo? = null,
        val metallicFactor: Float = 1f,
        val roughnessFactor: Float = 1f,
        val metallicRoughnessTexture: TextureInfo? = null,
        val normalTexture: TextureInfo? = null,
        val occlusionTexture: TextureInfo? = null,
        val emissiveTexture: TextureInfo? = null,
        val emissiveFactor: RgbColor = RgbColor(1f, 1f, 1f),
        override val alphaMode: AlphaMode = AlphaMode.OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
    ): Material()

    data class Unlit(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: TextureInfo? = null,
        override val alphaMode: AlphaMode = AlphaMode.OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
    ) : Material()

    data class Vanilla(
        override val name: String?,
        override val baseColor: RgbaColor = RgbaColor(1f, 1f, 1f, 1f),
        override val baseColorTexture: TextureInfo? = null,
        override val alphaMode: AlphaMode = AlphaMode.OPAQUE,
        override val alphaCutoff: Float = .5f,
        override val doubleSided: Boolean = false,
    ): Material()
}
