package top.fifthlight.armorstand.ui.component

import kotlinx.coroutines.*
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.Widget
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.texture.TextureManager
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import top.fifthlight.armorstand.manage.ModelManagerHolder
import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.armorstand.manage.model.ModelThumbnail
import top.fifthlight.armorstand.util.ThreadExecutorDispatcher
import top.fifthlight.blazerod.extension.NativeImageExt
import top.fifthlight.blazerod.model.Texture
import top.fifthlight.blazerod.model.util.readToBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.function.Consumer

class ModelIcon(
    private val modelItem: ModelItem,
) : AutoCloseable, Widget, Drawable, ResizableLayout {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ModelIcon::class.java)
        private val LOADING_ICON: Identifier = Identifier.of("armorstand", "loading")
        private const val ICON_WIDTH = 32
        private const val ICON_HEIGHT = 32
        private const val SMALL_ICON_WIDTH = 16
        private const val SMALL_ICON_HEIGHT = 16
    }

    private var closed = false
    private fun requireOpen() = require(!closed) { "Model icon already closed" }

    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    override fun setX(x: Int) {
        this.x = x
    }

    override fun setY(y: Int) {
        this.y = y
    }

    override fun getX(): Int = this.x
    override fun getY(): Int = this.y
    override fun getWidth(): Int = this.width
    override fun getHeight(): Int = this.height
    override fun setDimensions(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun forEachChild(consumer: Consumer<ClickableWidget>) = Unit

    private data class ModelTexture(
        val textureManager: TextureManager,
        val identifier: Identifier,
        val texture: NativeImageBackedTexture,
        val width: Int,
        val height: Int,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            textureManager.destroyTexture(identifier)
        }
    }

    private sealed class ModelIconState : AutoCloseable {
        override fun close() = Unit

        data object Loading : ModelIconState()
        data object None : ModelIconState()
        data object Failed : ModelIconState()
        data class Loaded(
            val icon: ModelTexture,
        ) : ModelIconState() {
            override fun close() = icon.close()
        }
    }

    private val scope = CoroutineScope(ThreadExecutorDispatcher(MinecraftClient.getInstance()) + Job())
    private var iconState: ModelIconState = ModelIconState.Loading

    private suspend fun loadIcon(
        path: Path,
        offsetAndLength: Pair<Long, Long>?,
        readSizeLimit: Int = 32 * 1024 * 1024,
        type: Texture.TextureType? = null,
    ): ModelTexture {
        val buffer = withContext(Dispatchers.IO) {
            FileChannel.open(path).use {
                offsetAndLength?.let { (offset, length) ->
                    it.readToBuffer(
                        offset = offset,
                        length = length,
                        readSizeLimit = readSizeLimit,
                    )
                } ?: it.readToBuffer(
                    readSizeLimit = readSizeLimit,
                )
            }
        }

        val width: Int
        val height: Int
        val identifier = Identifier.of("armorstand", "models/${modelItem.hash}")
        val texture = withContext(Dispatchers.Default) {
            if (type != null) {
                NativeImageExt.read(type, buffer)
            } else {
                NativeImage.read(buffer)
            }
        }.use { image ->
            width = image.width
            height = image.height
            NativeImageBackedTexture({ "Model icon for ${modelItem.hash}" }, image)
        }
        texture.setClamp(true)
        texture.setFilter(true, false)
        return try {
            val textureManager = MinecraftClient.getInstance().textureManager
            textureManager.registerTexture(identifier, texture)
            ModelTexture(
                textureManager = textureManager,
                identifier = identifier,
                texture = texture,
                width = width,
                height = height,
            )
        } catch (ex: Throwable) {
            texture.close()
            throw ex
        }
    }

    init {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                ModelManagerHolder.modelDir.resolve(modelItem.path).toAbsolutePath()
            }
            val thumbnail = ModelManagerHolder.instance.getModelThumbnail(modelItem)
            try {
                when (thumbnail) {
                    is ModelThumbnail.Embed -> {
                        val icon = loadIcon(
                            path = path,
                            offsetAndLength = Pair(thumbnail.offset, thumbnail.length),
                            type = thumbnail.type,
                        )
                        iconState = ModelIconState.Loaded(icon)
                    }

                    is ModelThumbnail.External -> {
                        val icon = loadIcon(
                            path = thumbnail.path,
                            offsetAndLength = null,
                        )
                        iconState = ModelIconState.Loaded(icon)
                    }

                    ModelThumbnail.None -> {
                        iconState = ModelIconState.None
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                LOGGER.warn("Failed to read model icon", ex)
                iconState = ModelIconState.Failed
            }
        }
    }

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float,
    ) {
        renderIconInternal(context, x, y, width, height)
    }

    private fun renderIconInternal(
        context: DrawContext,
        targetX: Int,
        targetY: Int,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        requireOpen()
        val imageWidth = targetWidth
        val imageHeight = targetHeight
        val left = targetX
        val top = targetY

        when (val state = iconState) {
            is ModelIconState.Loaded -> {
                val icon = state.icon
                val iconAspect = icon.width.toFloat() / icon.height.toFloat()
                val targetAspect = imageWidth.toFloat() / imageHeight.toFloat()

                if (iconAspect > targetAspect) {
                    val scaledHeight = (imageWidth / iconAspect).toInt()
                    val yOffset = (imageHeight - scaledHeight) / 2

                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        icon.identifier,
                        left,
                        top + yOffset,
                        0f,
                        0f,
                        imageWidth,
                        scaledHeight,
                        icon.width,
                        icon.height,
                        icon.width,
                        icon.height,
                    )

                    context.drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED,
                        modelItem.type.icon,
                        left + imageWidth - SMALL_ICON_WIDTH / 2,
                        top + yOffset + scaledHeight - SMALL_ICON_HEIGHT / 2,
                        SMALL_ICON_WIDTH,
                        SMALL_ICON_HEIGHT,
                    )
                } else {
                    val scaledWidth = (imageHeight * iconAspect).toInt()
                    val xOffset = (imageWidth - scaledWidth) / 2

                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        icon.identifier,
                        left + xOffset,
                        top,
                        0f,
                        0f,
                        scaledWidth,
                        imageHeight,
                        icon.width,
                        icon.height,
                        icon.width,
                        icon.height,
                    )

                    context.drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED,
                        modelItem.type.icon,
                        left + xOffset + scaledWidth - SMALL_ICON_WIDTH / 2,
                        top + imageHeight - SMALL_ICON_HEIGHT / 2,
                        SMALL_ICON_WIDTH,
                        SMALL_ICON_HEIGHT,
                    )
                }
            }

            ModelIconState.Loading -> {
                context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    LOADING_ICON,
                    left + (imageWidth - ICON_WIDTH) / 2,
                    top + (imageHeight - ICON_HEIGHT) / 2,
                    ICON_WIDTH,
                    ICON_HEIGHT,
                )
            }

            ModelIconState.None -> {
                context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    modelItem.type.icon,
                    left + (imageWidth - ICON_WIDTH) / 2,
                    top + (imageHeight - ICON_HEIGHT) / 2,
                    ICON_WIDTH,
                    ICON_HEIGHT,
                )
            }

            ModelIconState.Failed -> {}
        }
    }

    override fun close() {
        scope.cancel()
        iconState.close()
        closed = true
    }
}