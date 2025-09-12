package top.fifthlight.blazerod.model.bedrock.metadata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import top.fifthlight.blazerod.model.Metadata as GeneralMetadata

@Serializable
data class ModelMetadata(
    val spec: Int,
    val metadata: Metadata? = null,
    val properties: Properties? = null,
    val files: Files,
) {
    @Serializable
    data class Metadata(
        val name: String,
        val tips: String? = null,
        val license: License? = null,
        val authors: List<Author>? = null,
        val link: Link? = null,
    ) {
        @Serializable
        data class License(
            val type: String,
            val desc: String? = null,
        )

        @Serializable
        data class Author(
            val name: String,
            val role: String? = null,
            val contact: Contact? = null,
            val comment: String? = null,
        ) {
            @Serializable(with = ContactSerializer::class)
            data class Contact(val entries: List<Pair<String, String>>) {
                operator fun get(key: String): String? = entries.firstOrNull { it.first == key }?.second
            }

            object ContactSerializer : KSerializer<Contact> {
                override val descriptor: SerialDescriptor =
                    buildClassSerialDescriptor("Contact")

                override fun deserialize(decoder: Decoder): Contact {
                    require(decoder is JsonDecoder) { "This serializer can be used only with JSON" }
                    val jsonObject = decoder.decodeJsonElement() as? JsonObject
                        ?: throw IllegalArgumentException("Contact must be a JSON object")
                    val list = jsonObject.entries.map { (key, value) ->
                        key to value.jsonPrimitive.content
                    }
                    return Contact(list)
                }

                override fun serialize(encoder: Encoder, value: Contact) {
                    require(encoder is JsonEncoder) { "This serializer can be used only with JSON" }
                    val jsonObject = JsonObject(
                        value.entries.associate { (k, v) -> k to JsonPrimitive(v) }
                    )
                    encoder.encodeJsonElement(jsonObject)
                }
            }

        }

        @Serializable
        data class Link(
            val home: String? = null,
            val donate: String? = null,
        )

        fun toMetadata() = GeneralMetadata(
            title = name,
            comment = tips,
            licenseType = license?.type,
            licenseDescription = license?.desc,
            authors = authors?.map {
                GeneralMetadata.Author(
                    name = it.name,
                    role = it.role,
                    contact = it.contact?.entries,
                    comment = it.comment,
                )
            },
            linkHome = link?.home,
            linkDonate = link?.donate,
        )
    }

    @Serializable
    data class Properties(
        @SerialName("height_scale")
        val heightScale: Double? = null,
        @SerialName("width_scale")
        val widthScale: Double? = null,
        @SerialName("default_texture")
        val defaultTexture: String? = null,
        val free: Boolean,
    )

    @Serializable
    data class Files(
        val player: File,
    ) {
        @Serializable
        data class File(
            val model: Map<String, String>,
            val animation: Map<String, String>? = null,
            @SerialName("animation_controllers")
            val animationControllers: List<String>? = null,
            val texture: List<Texture>,
        )

        @Serializable(with = TextureSerializer::class)
        sealed class Texture {
            @Serializable
            data class Path(val path: String) : Texture()

            @Serializable
            data class Pbr(
                val uv: String,
                val normal: String? = null,
                val specular: String? = null,
            ) : Texture()
        }

        object TextureSerializer : KSerializer<Texture> {
            override val descriptor = buildClassSerialDescriptor("Texture") {
                element<String>("path")
                element<String>("uv")
                element<String?>("normal")
                element<String?>("specular")
            }

            override fun deserialize(decoder: Decoder): Texture {
                require(decoder is JsonDecoder) { "This serializer can be used only with JSON" }
                return when (val element = decoder.decodeJsonElement()) {
                    is JsonPrimitive -> Texture.Path(element.content)
                    is JsonObject -> {
                        val uv = element["uv"]?.jsonPrimitive?.content
                            ?: throw IllegalArgumentException("Missing 'uv' in texture object")
                        val normal = element["normal"]?.jsonPrimitive?.contentOrNull
                        val specular = element["specular"]?.jsonPrimitive?.contentOrNull
                        Texture.Pbr(uv, normal, specular)
                    }

                    else -> throw IllegalArgumentException("Invalid texture format")
                }
            }

            override fun serialize(encoder: Encoder, value: Texture) {
                require(encoder is JsonEncoder) { "This serializer can be used only with JSON" }
                val element = when (value) {
                    is Texture.Path -> JsonPrimitive(value.path)
                    is Texture.Pbr -> buildJsonObject {
                        put("uv", JsonPrimitive(value.uv))
                        value.normal?.let { put("normal", JsonPrimitive(it)) }
                        value.specular?.let { put("specular", JsonPrimitive(it)) }
                    }
                }
                encoder.encodeJsonElement(element)
            }
        }
    }
}
