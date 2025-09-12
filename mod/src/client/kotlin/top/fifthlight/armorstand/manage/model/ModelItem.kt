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
    val type by lazy { Type.of(path) }

    enum class Type(
        val icon: Identifier,
        val extensions: Set<String> = setOf(),
        val markerFiles: Set<String> = setOf(),
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
        JSON(
            icon = Identifier.of("armorstand", "thumbnail_json"),
            markerFiles = setOf("ysm.json", "model.json"),
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

            private val markerFileToTypeMap = buildMap {
                for (type in Type.entries) {
                    for (markerFile in type.markerFiles) {
                        put(markerFile, type)
                    }
                }
            }

            fun of(path: Path): Type {
                val fileName = path.fileName.toString().lowercase()
                markerFileToTypeMap[fileName]?.let { return it }
                val extension = path.extension
                extensionToTypeMap[extension]?.let { return it }
                return UNKNOWN
            }
        }
    }
}