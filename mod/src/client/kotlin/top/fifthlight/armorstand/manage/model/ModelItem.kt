package top.fifthlight.armorstand.manage.model

import net.minecraft.util.Identifier
import top.fifthlight.armorstand.util.ModelHash
import java.nio.file.Path
import kotlin.io.path.extension

data class ModelItem(
    val path: Path,
    val name: String,
    val lastChanged: Long,
    val hash: ModelHash,
    var favorite: Boolean = false,
) {
    val type by lazy {
        val extension = path.extension.lowercase()
        Type.of(extension)
    }

    enum class Type(
        val icon: Identifier,
        val extensions: Set<String>,
    ) {
        GLTF(
            icon = Identifier.of("armorstand", "thumbnail_gltf"),
            extensions = setOf("gltf", "glb"),
        ),
        VRM(
            icon = Identifier.of("armorstand", "thumbnail_vrm"),
            extensions = setOf("vrm"),
        ),
        PMX(
            icon = Identifier.of("armorstand", "thumbnail_pmx"),
            extensions = setOf("pmx"),
        ),
        PMD(
            icon = Identifier.of("armorstand", "thumbnail_pmd"),
            extensions = setOf("pmd"),
        ),
        FBX(
            icon = Identifier.of("armorstand", "thumbnail_fbx"),
            extensions = setOf("fbx"),
        ),
        OBJ(
            icon = Identifier.of("armorstand", "thumbnail_obj"),
            extensions = setOf("obj"),
        ),
        UNKNOWN(
            icon = Identifier.of("armorstand", "thumbnail_unknown"),
            extensions = setOf(),
        );

        companion object {
            private val extensionToTypeMap = buildMap {
                for (type in Type.entries) {
                    for (extension in type.extensions) {
                        put(extension, type)
                    }
                }
            }

            fun of(extension: String): Type = extensionToTypeMap[extension] ?: UNKNOWN
        }
    }
}