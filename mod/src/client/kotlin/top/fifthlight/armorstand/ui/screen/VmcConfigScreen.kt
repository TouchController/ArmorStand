package top.fifthlight.armorstand.ui.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.Positioner
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.ui.component.BorderLayout
import top.fifthlight.armorstand.ui.component.Insets
import top.fifthlight.armorstand.ui.component.LinearLayout
import top.fifthlight.armorstand.ui.component.Surface
import top.fifthlight.armorstand.ui.model.VmcConfigScreenModel
import top.fifthlight.armorstand.ui.util.textField
import top.fifthlight.armorstand.vmc.VmcMarionetteManager

class VmcConfigScreen(parent: Screen? = null) : ArmorStandScreen<VmcConfigScreen, VmcConfigScreenModel>(
    title = Text.translatable("armorstand.vmc"),
    parent = parent,
    viewModelFactory = ::VmcConfigScreenModel,
) {
    private val topBar by lazy {
        TextWidget(width, 32, title, currentClient.textRenderer)
    }
    private val closeButton = ButtonWidget.builder(ScreenTexts.BACK) { close() }.build()

    private val statusLabel by lazy {
        TextWidget(Text.empty(), currentClient.textRenderer).apply {
            scope.launch {
                viewModel.uiState.collect {
                    message = when (it.state) {
                        is VmcMarionetteManager.State.Running -> Text.translatable(
                            "armorstand.vmc.running",
                            it.state.port
                        )

                        is VmcMarionetteManager.State.Stopped -> Text.translatable("armorstand.vmc.stopped")

                        is VmcMarionetteManager.State.Failed -> Text.translatable(
                            "armorstand.vmc.failed",
                            it.state.exception.message
                        )
                    }
                }
            }
        }
    }

    private val portLabel by lazy {
        TextWidget(Text.translatable("armorstand.vmc.port"), currentClient.textRenderer)
    }

    private val portField by lazy {
        val textContent = MutableStateFlow(viewModel.uiState.value.portNumber.toString())
        scope.launch {
            textContent.collect { portStr ->
                portStr.toIntOrNull()?.let { port ->
                    ConfigHolder.update {
                        copy(vmcUdpPort = port)
                    }
                }
            }
        }
        textField(
            text = textContent,
            onChanged = {
                textContent.value = it
            },
        )
    }

    private val startButton = ButtonWidget.builder(Text.translatable("armorstand.vmc.start")) {
        when (viewModel.uiState.value.state) {
            VmcMarionetteManager.State.Stopped, is VmcMarionetteManager.State.Failed -> viewModel.startVmcClient()
            is VmcMarionetteManager.State.Running -> viewModel.stopVmcClient()
        }
    }.build().apply {
        scope.launch {
            viewModel.uiState.collect {
                message = when (it.state) {
                    VmcMarionetteManager.State.Stopped, is VmcMarionetteManager.State.Failed ->
                        Text.translatable("armorstand.vmc.start")

                    is VmcMarionetteManager.State.Running -> Text.translatable("armorstand.vmc.stop")
                }
            }
        }
    }

    override fun init() {
        val rootLayout = BorderLayout(
            width = width,
            height = height,
            direction = BorderLayout.Direction.VERTICAL,
        )
        rootLayout.setFirstElement(topBar) { topBar, width, height -> topBar.width = width }
        rootLayout.setCenterElement(
            widget = LinearLayout(
                width = 192,
                direction = LinearLayout.Direction.VERTICAL,
                padding = Insets(8),
                gap = 8,
                surface = Surface.listBackgroundWithSeparator(),
            ).apply {
                add(statusLabel, expand = true)
                add(
                    widget = BorderLayout(
                        width = 192,
                        height = portField.height,
                        direction = BorderLayout.Direction.HORIZONTAL,
                    ).apply {
                        setFirstElement(portLabel, Positioner.create().marginRight(8))
                        setCenterElement(portField)
                    },
                    expand = true,
                )
                add(startButton, expand = true)
                pack()
                addDrawable(this)
            },
            positioner = Positioner.create().alignVerticalCenter().alignHorizontalCenter(),
            onSizeChanged = { _, _, _ -> },
        )
        rootLayout.setSecondElement(
            LinearLayout(
                width = width,
                height = 32,
                direction = LinearLayout.Direction.HORIZONTAL,
                align = LinearLayout.Align.CENTER,
                gap = 8,
            ).apply {
                add(closeButton, Positioner.create().apply { alignVerticalCenter() })
            })
        rootLayout.refreshPositions()
        rootLayout.forEachChild { addDrawableChild(it) }
    }
}