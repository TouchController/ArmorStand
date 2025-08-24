package top.fifthlight.armorstand.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.model.renderer.ComputeShaderTransformRenderer
import top.fifthlight.blazerod.model.renderer.CpuTransformRenderer
import top.fifthlight.blazerod.model.renderer.Renderer
import top.fifthlight.blazerod.model.renderer.VertexShaderTransformRenderer
import java.nio.file.InvalidPathException
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Serializable
data class GlobalConfig(
    val model: String? = null,
    val showOtherPlayerModel: Boolean = true,
    val sendModelData: Boolean = true,
    val hidePlayerShadow: Boolean = false,
    val modelScale: Float = 1f,
    val thirdPersonDistanceScale: Float = 1f,
    val invertHeadDirection: Boolean = false,
    val renderer: RendererKey = RendererKey.VERTEX_SHADER_TRANSFORM,
    val vmcUdpPort: Int = 9000,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalConfig::class.java)
    }

    @Serializable
    enum class RendererKey(
        val nameKey: String,
        val type: Renderer.Type<*, *>,
    ) {
        @SerialName("vertex")
        VERTEX_SHADER_TRANSFORM(
            nameKey = "armorstand.renderer.vertex_shader_transform",
            type = VertexShaderTransformRenderer.Type,
        ),

        @SerialName("cpu")
        CPU_TRANSFORM(
            nameKey = "armorstand.renderer.cpu_transform",
            type = CpuTransformRenderer.Type,
        ),

        @SerialName("compute")
        COMPUTE_SHADER_TRANSFORM(
            nameKey = "armorstand.renderer.compute_shader_transform",
            type = ComputeShaderTransformRenderer.Type,
        ),
    }

    val modelPath by lazy {
        try {
            model?.let { Path(it) }
        } catch (ex: InvalidPathException) {
            logger.warn("Bad model path", ex)
            null
        }
    }
}

object ConfigHolder {
    private val configFile = FabricLoader.getInstance().configDir.resolve("armorstand.json")
    private val _config = MutableStateFlow(GlobalConfig())
    val config = _config.asStateFlow()

    @OptIn(ExperimentalSerializationApi::class)
    fun read() {
        runCatching {
            _config.value = configFile.inputStream().use { Json.decodeFromStream<GlobalConfig>(it) }
        }
    }

    fun update(editor: GlobalConfig.() -> GlobalConfig) {
        save(_config.updateAndGet(editor))
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun save(config: GlobalConfig) {
        configFile.parent.createDirectories()
        configFile.outputStream().use { Json.encodeToStream<GlobalConfig>(config, it) }
    }
}