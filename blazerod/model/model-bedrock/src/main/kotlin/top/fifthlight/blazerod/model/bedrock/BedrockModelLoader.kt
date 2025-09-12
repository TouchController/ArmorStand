package top.fifthlight.blazerod.model.bedrock

import kotlinx.serialization.json.Json
import top.fifthlight.blazerod.model.ModelFileLoader
import top.fifthlight.blazerod.model.bedrock.metadata.ModelMetadata
import top.fifthlight.blazerod.model.util.readToBuffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class BedrockModelLoadException(message: String) : Exception(message)

class BedrockModelLoader : ModelFileLoader {
    override val abilities = setOf(ModelFileLoader.Ability.MODEL, ModelFileLoader.Ability.METADATA)
    override val markerFiles = mapOf(
        "ysm.json" to abilities,
        "model.json" to abilities,
    )
    override val probeLength: Int? = null

    override fun probe(buffer: ByteBuffer) = false

    private val format = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun readMetadataFile(path: Path): ModelMetadata {
        val metadataString = FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            // Will any file be larger than 1MB?
            val buffer = channel.readToBuffer(readSizeLimit = 1024 * 1024)
            StandardCharsets.UTF_8.decode(buffer).toString()
        }
        return format.decodeFromString<ModelMetadata>(metadataString).also {
            if (it.spec < 2) {
                throw BedrockModelLoadException("Unsupported model spec: ${it.spec}")
            }
        }
    }

    companion object {
        private val EMPTY_LOAD_RESULT = ModelFileLoader.LoadResult(
            metadata = null,
            model = null,
            animations = listOf(),
        )
    }

    override fun load(
        path: Path,
        basePath: Path,
    ): ModelFileLoader.LoadResult {
        val metadata = readMetadataFile(path)

        val mainModelPathStr = metadata.files.player.model["main"] ?: return EMPTY_LOAD_RESULT
        val mainModelPathPath = basePath.resolve(mainModelPathStr)
        val mainModel = BedrockModelJsonLoader(
            basePath = basePath,
            file = metadata.files.player,
        ).load(mainModelPathPath)

        return ModelFileLoader.LoadResult(
            metadata = metadata.metadata?.toMetadata(),
            model = mainModel,
            animations = listOf(),
        )
    }

    override fun getMarkerFileHashes(marker: Path, directory: Path): Set<Path> {
        val metadata = readMetadataFile(marker)
        val playerModelFiles = metadata.files.player.model.values
        val playerAnimationFiles = metadata.files.player.animation?.values ?: listOf()
        return (playerModelFiles + playerAnimationFiles).map { directory.resolve(it) }.toSet()
    }

    override fun getMetadata(path: Path, basePath: Path?): ModelFileLoader.MetadataResult {
        val metadata = readMetadataFile(path)
        return metadata.metadata?.toMetadata()?.let {
            ModelFileLoader.MetadataResult.Success(it)
        } ?: ModelFileLoader.MetadataResult.None
    }
}