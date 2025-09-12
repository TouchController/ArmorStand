package top.fifthlight.armorstand.ui.screen

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.Identifier
import top.fifthlight.armorstand.ArmorStandClient
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.armorstand.state.ModelInstanceManager
import top.fifthlight.armorstand.ui.component.ModelIcon
import top.fifthlight.armorstand.ui.component.Surface
import top.fifthlight.armorstand.ui.model.ModelSwitchViewModel
import top.fifthlight.armorstand.ui.state.ModelSwitchScreenState

class ModelSwitchScreen(parent: Screen? = null) : ArmorStandScreen<ModelSwitchScreen, ModelSwitchViewModel>(
    parent = parent,
    viewModelFactory = ::ModelSwitchViewModel,
    title = Text.translatable("armorstand.model_switch"),
) {

    companion object {
        private val LOADING_ICON: Identifier = Identifier.of("armorstand", "loading")
        private const val LOADING_ICON_WIDTH = 32
        private const val LOADING_ICON_HEIGHT = 32
        private const val ITEM_SIZE = 72
        private const val ICON_SIZE = 56
        private const val ICON_PADDING = (ITEM_SIZE - ICON_SIZE) / 2
        private const val ITEM_GAP = 16
        const val TOTAL_ITEMS = ModelSwitchViewModel.TOTAL_ITEMS
        private const val TOTAL_WIDTH = ITEM_SIZE * TOTAL_ITEMS + ITEM_GAP * (TOTAL_ITEMS - 1)
        private val UNSELECTED_BACKGROUND
            get() = Surface.listBackgroundWithSeparator()
        private val SELECTED_BACKGROUND
            get() = Surface.listBackgroundWithSeparator() + Surface.color(0x66FFFFFFu)
        private val MODEL_NAME_LABEL_BACKGROUND
            get() = Surface.listBackgroundWithSeparator()
        private const val MODEL_NAME_LABEL_PADDING = 16
        private const val MODEL_NAME_LABEL_WIDTH = 320
        private const val MODEL_NAME_LABEL_HEIGHT = 32
        private const val MODEL_ICON_CACHE_SIZE = 32
        private val EMPTY_MESSAGE = Text.translatable("armorstand.model_switch.empty")
        private val NO_SELECTION_MESSAGE = Text.translatable("armorstand.model_switch.no_selection")
    }

    override fun shouldPause() = false
    override fun applyBlur(context: DrawContext) {}
    override fun renderDarkening(context: DrawContext) {}

    private val modelIconCache = LinkedHashMap<Int, Pair<ModelIcon, ModelItem>>()
    private var modelIcons = listOf<Pair<ModelIcon, ModelItem>>()

    init {
        val leftModels = TOTAL_ITEMS / 2
        val rightModels = TOTAL_ITEMS - leftModels - 1
        scope.launch {
            try {
                viewModel.uiState
                    .mapNotNull {
                        when (val content = it.content) {
                            is ModelSwitchScreenState.Content.Loaded -> {
                                Pair(content.currentIndex, content.totalModels)
                            }

                            else -> null
                        }
                    }
                    .distinctUntilChanged()
                    .collect { (currentIndex, totalModels) ->
                        if (totalModels.isEmpty()) {
                            return@collect
                        }
                        val realIndices = mutableListOf<Int>()
                        modelIcons = (-leftModels..rightModels).map {
                            val realIndex = (currentIndex + it + totalModels.size) % totalModels.size
                            realIndices.add(realIndex)
                            val modelItem = totalModels[realIndex]
                            modelIconCache.getOrPut(realIndex) {
                                val icon = ModelIcon(modelItem).apply { setDimensions(ICON_SIZE, ICON_SIZE) }
                                Pair(icon, modelItem)
                            }
                        }
                        while (modelIconCache.size > MODEL_ICON_CACHE_SIZE) {
                            val lastEntry = modelIconCache.entries.last()
                            if (lastEntry.value !in modelIcons) {
                                modelIconCache.remove(lastEntry.key)
                            } else {
                                break
                            }
                        }
                    }
            } finally {
                modelIconCache.values.forEach { it.first.close() }
                modelIconCache.clear()
            }
        }
    }

    override fun tick() {
        viewModel.clientTick()
        val uiState = viewModel.uiState.value
        if (uiState.content == ModelSwitchScreenState.Content.Empty) {
            currentClient.player?.sendMessage(EMPTY_MESSAGE, true)
            close()
            return
        }
        if (uiState.needToBeClosed) {
            val currentModel = modelIcons.getOrNull(TOTAL_ITEMS / 2)
            if (currentModel != null) {
                val (_, item) = currentModel
                ModelInstanceManager.addFavoriteModelPath(item.path)
                ConfigHolder.update {
                    copy(model = item.path.toString())
                }
            }
            close()
            return
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true
        }
        return when {
            verticalAmount > 0.0 -> {
                switchModel(false)
                true
            }

            verticalAmount < 0.0 -> {
                switchModel(true)
                true
            }

            else -> false
        }
    }

    private var totalMoveDelta = 0.0
    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val mouse = currentClient.mouse
        totalMoveDelta += mouse.cursorDeltaX
        when {
            totalMoveDelta > ITEM_SIZE -> {
                switchModel(true)
                totalMoveDelta = 0.0
            }

            totalMoveDelta < -ITEM_SIZE -> {
                switchModel(false)
                totalMoveDelta = 0.0
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (ArmorStandClient.modelSwitchKeyBinding.matchesKey(keyCode, scanCode)) {
            switchModel(true)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun switchModel(next: Boolean) {
        currentClient.soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F))
        viewModel.switchModel(next)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val uiState = viewModel.uiState.value
        when (uiState.content) {
            ModelSwitchScreenState.Content.Loading -> {
                context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    LOADING_ICON,
                    (width - LOADING_ICON_WIDTH) / 2,
                    (height - LOADING_ICON_HEIGHT) / 2,
                    LOADING_ICON_WIDTH,
                    LOADING_ICON_HEIGHT,
                )
            }

            ModelSwitchScreenState.Content.Empty -> {}

            is ModelSwitchScreenState.Content.Loaded -> {
                val left = (width - TOTAL_WIDTH) / 2
                val top = ITEM_GAP
                for (index in 0 until TOTAL_ITEMS) {
                    val itemLeft = left + index * (ITEM_SIZE + ITEM_GAP)
                    if (index == TOTAL_ITEMS / 2) {
                        SELECTED_BACKGROUND
                    } else {
                        UNSELECTED_BACKGROUND
                    }.draw(context, itemLeft, top, ITEM_SIZE, ITEM_SIZE)
                    val (icon) = modelIcons.getOrNull(index) ?: continue
                    icon.setPosition(itemLeft + ICON_PADDING, top + ICON_PADDING)
                    icon.render(context, mouseX, mouseY, deltaTicks)
                }

                val labelLeft = (width - MODEL_NAME_LABEL_WIDTH) / 2
                val labelTop = top + ITEM_SIZE + ITEM_GAP
                MODEL_NAME_LABEL_BACKGROUND.draw(
                    context,
                    labelLeft,
                    labelTop,
                    MODEL_NAME_LABEL_WIDTH,
                    MODEL_NAME_LABEL_HEIGHT
                )
                val currentModel = modelIcons.getOrNull(TOTAL_ITEMS / 2)
                val labelTextLeft = labelLeft + MODEL_NAME_LABEL_PADDING
                val labelTextWidth = MODEL_NAME_LABEL_WIDTH - MODEL_NAME_LABEL_PADDING * 2
                val textRenderer = currentClient.textRenderer
                if (currentModel != null) {
                    val (_, item) = currentModel
                    val text = Text.literal(item.name)
                    val textLines = textRenderer.wrapLines(text, labelTextWidth)
                    val textHeight = textRenderer.fontHeight * textLines.size
                    val textWidth = textLines.maxOf { textRenderer.getWidth(it) }
                    var textTextTop = labelTop + (MODEL_NAME_LABEL_HEIGHT - textHeight) / 2
                    for (line in textLines) {
                        context.drawTextWithShadow(
                            textRenderer,
                            line,
                            labelTextLeft + (labelTextWidth - textWidth) / 2,
                            textTextTop,
                            Colors.WHITE,
                        )
                        textTextTop += textRenderer.fontHeight
                    }
                } else {
                    val text = NO_SELECTION_MESSAGE
                    val textWidth = textRenderer.getWidth(text)
                    val textTextTop = labelTop + (MODEL_NAME_LABEL_HEIGHT - textRenderer.fontHeight) / 2
                    context.drawTextWithShadow(
                        textRenderer,
                        text,
                        labelTextLeft + (labelTextWidth - textWidth) / 2,
                        textTextTop,
                        Colors.WHITE,
                    )
                }
            }
        }

        super.render(context, mouseX, mouseY, deltaTicks)
    }
}
