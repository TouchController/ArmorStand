package top.fifthlight.armorstand.ui.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.Identifier
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.armorstand.util.ThreadExecutorDispatcher

class ModelButton(
    x: Int = 0,
    y: Int = 0,
    width: Int = 0,
    height: Int = 0,
    private val modelItem: ModelItem,
    private val textRenderer: TextRenderer,
    private val padding: Insets = Insets(),
    onPressAction: (ModelItem) -> Unit,
    private val onFavoriteAction: (ModelItem) -> Unit,
) : ButtonWidget(
    x,
    y,
    width,
    height,
    Text.literal(modelItem.name),
    { onPressAction.invoke(modelItem) },
    DEFAULT_NARRATION_SUPPLIER,
), AutoCloseable {
    companion object {
        private val STAR_ICON: Identifier = Identifier.of("armorstand", "star")
        private val STAR_EMPTY_ICON: Identifier = Identifier.of("armorstand", "star_empty")
        private val STAR_HOVERED_ICON: Identifier = Identifier.of("armorstand", "star_hovered")
        private val STAR_EMPTY_HOVERED_ICON: Identifier = Identifier.of("armorstand", "star_empty_hovered")
        private const val STAR_ICON_SIZE = 9
        private const val STAR_ICON_PADDING = 4
    }

    private var closed = false
    private fun requireOpen() = require(!closed) { "Model button already closed" }

    private val scope = CoroutineScope(ThreadExecutorDispatcher(MinecraftClient.getInstance()) + Job())
    private var checked = false
    private val modelIcon = ModelIcon(modelItem)

    init {
        scope.launch {
            ConfigHolder.config.map { it.modelPath }.distinctUntilChanged().collect {
                checked = it == modelItem.path
            }
        }
    }

    private val favoriteButtonXRange
        get() = x + width - STAR_ICON_SIZE - STAR_ICON_PADDING until x + width - STAR_ICON_PADDING
    private val favoriteButtonYRange
        get() = y + STAR_ICON_PADDING until y + STAR_ICON_SIZE + STAR_ICON_PADDING

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val clickedFavoriteButton = mouseX.toInt() in favoriteButtonXRange && mouseY.toInt() in favoriteButtonYRange
        if (active && visible && isValidClickButton(button) && clickedFavoriteButton) {
            playDownSound(MinecraftClient.getInstance().getSoundManager())
            onFavoriteAction.invoke(modelItem)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float,
    ) {
        requireOpen()
        if (active && checked) {
            context.fill(
                x,
                y,
                x + width,
                y + height,
                0x66000000u.toInt(),
            )
        }
        if (active && hovered) {
            context.drawBorder(
                x,
                y,
                width,
                height,
                0x99000000u.toInt(),
            )
        } else if (isSelected) {
            context.drawBorder(
                x,
                y,
                width,
                height,
                0x44000000u.toInt(),
            )
        }
        val mouseInFavoriteIcon = mouseX in favoriteButtonXRange && mouseY in favoriteButtonYRange
        if (modelItem.favorite) {
            context.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED,
                if (mouseInFavoriteIcon) {
                    STAR_HOVERED_ICON
                } else {
                    STAR_ICON
                },
                favoriteButtonXRange.first,
                favoriteButtonYRange.first,
                STAR_ICON_SIZE,
                STAR_ICON_SIZE,
            )
        } else if (hovered) {
            context.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED,
                if (mouseInFavoriteIcon) {
                    STAR_EMPTY_HOVERED_ICON
                } else {
                    STAR_EMPTY_ICON
                },
                favoriteButtonXRange.first,
                favoriteButtonYRange.first,
                STAR_ICON_SIZE,
                STAR_ICON_SIZE,
            )
        }

        val top = y + padding.top
        val bottom = y + height - padding.bottom
        val left = x + padding.left
        val right = x + width - padding.right

        val imageBottom = bottom - textRenderer.fontHeight - 8
        val imageWidth = right - left
        val imageHeight = imageBottom - top
        modelIcon.setPosition(left, top)
        modelIcon.setDimensions(imageWidth, imageHeight)
        modelIcon.render(context, mouseX, mouseY, deltaTicks)

        drawScrollableText(
            context,
            textRenderer,
            message,
            left,
            bottom - textRenderer.fontHeight,
            right,
            bottom,
            Colors.WHITE,
        )
    }

    override fun close() {
        scope.cancel()
        modelIcon.close()
        closed = true
    }
}
