package top.fifthlight.armorstand.ui.screen

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tab.TabManager
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.Positioner
import net.minecraft.client.gui.widget.TabNavigationWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW
import top.fifthlight.armorstand.ArmorStandClient
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.manage.ModelManager
import top.fifthlight.armorstand.ui.component.*
import top.fifthlight.armorstand.ui.model.ConfigViewModel
import top.fifthlight.armorstand.ui.util.autoWidthButton
import top.fifthlight.armorstand.ui.util.checkbox
import top.fifthlight.armorstand.ui.util.slider
import top.fifthlight.armorstand.ui.util.textField
import top.fifthlight.armorstand.util.ceilDiv

class ConfigScreen(parent: Screen? = null) : ArmorStandScreen<ConfigScreen, ConfigViewModel>(
    parent = parent,
    viewModelFactory = ::ConfigViewModel,
    title = Text.translatable("armorstand.config"),
) {
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_1 && hasControlDown() && ArmorStandClient.debug) {
            client?.setScreen(DebugScreen(this))
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private val topBar by lazy {
        TextWidget(width, 32, title, currentClient.textRenderer)
    }

    private val closeButton = ButtonWidget.builder(ScreenTexts.BACK) { close() }.build()

    private val openModelDirectoryButton =
        ButtonWidget.builder(Text.translatable("armorstand.config.open_model_directory")) {
            viewModel.openModelDir()
        }.build()

    private val sortButton = run {
        fun sortText(order: ModelManager.Order, ascend: Boolean): String {
            val order = when (order) {
                ModelManager.Order.NAME -> "name"
                ModelManager.Order.LAST_CHANGED -> "last_changed"
            }
            val sort = when (ascend) {
                true -> "asc"
                false -> "desc"
            }
            return "armorstand.config.sort.$order.$sort"
        }

        ButtonWidget.builder(Text.translatable("armorstand.config.sort.name.asc")) {
            val (order, ascend) = viewModel.uiState.value.let { Pair(it.order, it.sortAscend) }
            if (ascend) {
                // switch ascend
                viewModel.updateSearchParam(order, false)
            } else {
                // next order
                val index = (ModelManager.Order.entries.indexOf(order) + 1) % ModelManager.Order.entries.size
                val newOrder = ModelManager.Order.entries[index]
                viewModel.updateSearchParam(newOrder, true)
            }
        }.size(100, 20).build().also { button ->
            scope.launch {
                viewModel.uiState.collect { state ->
                    button.message = Text.translatable(sortText(state.order, state.sortAscend))
                }
            }
        }
    }

    private val refreshButton by lazy {
        autoWidthButton(Text.translatable("armorstand.config.refresh")) {
            viewModel.refreshModels()
        }
    }

    private val clearButton by lazy {
        autoWidthButton(Text.translatable("armorstand.config.clear")) {
            viewModel.selectModel(null)
        }.also { button ->
            scope.launch {
                ConfigHolder.config.collect { config ->
                    button.active = config.model != null
                }
            }
        }
    }

    private val searchBox by lazy {
        textField(
            placeHolder = Text.translatable("armorstand.config.search_placeholder")
                .formatted(Formatting.ITALIC)
                .formatted(Formatting.GRAY),
            text = viewModel.uiState.map { it.searchString }.distinctUntilChanged(),
            onChanged = viewModel::updateSearchString,
        )
    }

    private val pager by lazy {
        PagingWidget(
            textRenderer = textRenderer,
            currentPage = viewModel.uiState.value.currentOffset,
            totalPages = viewModel.uiState.value.totalItems,
            onPrevPage = {
                viewModel.updatePageIndex(-1)
            },
            onNextPage = {
                viewModel.updatePageIndex(1)
            },
        ).also { pager ->
            scope.launch {
                viewModel.uiState.map { state ->
                    Triple(state.currentOffset, state.totalItems, state.pageSize)
                }.distinctUntilChanged().collect { (currentOffset, totalItems, pageSize) ->
                    pageSize?.let { pageSize ->
                        pager.currentPage = (currentOffset / pageSize) + 1
                        pager.totalPages = totalItems ceilDiv pageSize
                        pager.refresh()
                    }
                }
            }
        }
    }

    private val modelGrid by lazy {
        AutoHeightGridLayout(
            cellWidth = 64,
            cellHeightRange = 60..80,
            padding = Insets(horizonal = 8),
            verticalGap = 8,
        ).also { grid ->
            scope.launch {
                viewModel.uiState.map { it.currentPageItems }.distinctUntilChanged().collect { items ->
                    grid.forEachChild {
                        if (it is AutoCloseable) {
                            it.close()
                        }
                        remove(it)
                    }
                    grid.clear()
                    items?.let {
                        for (item in items) {
                            val button = ModelButton(
                                modelItem = item,
                                textRenderer = textRenderer,
                                padding = Insets(8),
                                onPressAction = {
                                    viewModel.selectModel(it.path)
                                },
                                onFavoriteAction = {
                                    viewModel.setFavoriteModel(it.path, !it.favorite)
                                },
                            )
                            grid.add(button)
                        }
                        grid.refreshPositions()
                        grid.forEachChild { addDrawableChild(it) }
                    }
                }
            }
        }
    }

    private val loadingOverlay by lazy {
        LoadingOverlay(modelGrid).also { overlay ->
            scope.launch {
                viewModel.uiState.collect { state ->
                    overlay.loading = state.currentPageItems == null
                }
            }
        }
    }

    private val sendModelDataButton by lazy {
        checkbox(
            text = Text.translatable("armorstand.config.send_model_data"),
            value = viewModel.uiState.map { it.sendModelData },
            onValueChanged = viewModel::updateSendModelData,
        )
    }

    private val hidePlayerShadowButton by lazy {
        checkbox(
            text = Text.translatable("armorstand.config.hide_player_shadow"),
            value = viewModel.uiState.map { it.hidePlayerShadow },
            onValueChanged = viewModel::updateHidePlayerShadow,
        )
    }

    private val showOtherPlayersButton by lazy {
        checkbox(
            text = Text.translatable("armorstand.config.show_other_players"),
            value = viewModel.uiState.map { it.showOtherPlayerModel },
            onValueChanged = viewModel::updateShowOtherPlayerModel,
        )
    }

    private val invertHeadDirectionButton by lazy {
        checkbox(
            text = Text.translatable("armorstand.config.invert_head_direction"),
            value = viewModel.uiState.map { it.invertHeadDirection },
            onValueChanged = viewModel::updateInvertHeadDirection,
        )
    }

    private val modelScaleSlider by lazy {
        slider(
            textFactory = { slider, text -> Text.translatable("armorstand.config.model_scale", text) },
            min = 0.0,
            max = 4.0,
            value = viewModel.uiState.map { it.modelScale.toDouble() },
            onValueChanged = { userTriggered, value ->
                viewModel.updateModelScale(value.toFloat())
            },
        )
    }

    private val thirdPersonDistanceScaleSlider = slider(
        textFactory = { slider, text -> Text.translatable("armorstand.config.third_person_distance_scale", text) },
        min = 0.05,
        max = 2.0,
        value = viewModel.uiState.map { it.thirdPersonDistanceScale.toDouble() },
        onValueChanged = { userTriggered, value ->
            viewModel.updateThirdPersonDistanceScale(value.toFloat())
        },
    )

    private val rendererSelectButton = ButtonWidget.builder(Text.translatable("armorstand.config.renderer_select")) {
        currentClient.setScreen(RendererSelectScreen(this))
    }.build()

    private val vmcButton = ButtonWidget.builder(Text.translatable("armorstand.config.vmc")) {
        currentClient.setScreen(VmcConfigScreen(this))
    }.build()

    private val previewTab = LayoutScreenTab(
        title = Text.translatable("armorstand.config.tab.preview"),
        padding = Insets(8),
    ) {
        BorderLayout(
            direction = BorderLayout.Direction.VERTICAL,
        ).apply {
            setCenterElement(
                ModelWidget(
                    surface = Surface.color(0xFF383838u) + Surface.border(0xFF161616u)
                ),
            )
            val gap = 8
            val padding = 8
            setSecondElement(
                LinearLayout(
                    direction = LinearLayout.Direction.VERTICAL,
                    padding = Insets(top = padding),
                    gap = gap,
                ).apply {
                    listOf(
                        invertHeadDirectionButton,
                        modelScaleSlider,
                    ).forEach {
                        add(
                            it,
                            expand = true
                        )
                    }
                    pack()
                }
            )
        }
    }

    private val settingsTab = LayoutScreenTab(
        title = Text.translatable("armorstand.config.tab.settings"),
        padding = Insets(8),
    ) {
        BorderLayout(
            direction = BorderLayout.Direction.VERTICAL,
        ).apply {
            setCenterElement(
                LinearLayout(
                    direction = LinearLayout.Direction.VERTICAL,
                    padding = Insets(top = 8),
                    gap = 8,
                ).apply {
                    listOf(
                        rendererSelectButton,
                        vmcButton,
                        sendModelDataButton,
                        showOtherPlayersButton,
                        hidePlayerShadowButton,
                        thirdPersonDistanceScaleSlider,
                    ).forEach {
                        add(
                            it,
                            expand = true
                        )
                    }
                }
            )
        }
    }

    private val metadataTab = LayoutScreenTab(
        title = Text.translatable("armorstand.config.tab.metadata"),
        padding = Insets(8),
    ) {
        BorderLayout().apply {
            setCenterElement(
                MetadataWidget(
                    client = currentClient,
                    textClickHandler = ::handleTextClick,
                ).also {
                scope.launch {
                    viewModel.uiState.collect { state ->
                        it.metadata = state.currentMetadata
                    }
                }
            }, Positioner.create().margin(8))
        }
    }

    private val tabManager = TabManager(
        { addDrawableChild(it) },
        { remove(it) },
    )

    private var tabNavigationWidget = TabNavigationWidget.builder(tabManager, width)
        .tabs(previewTab, settingsTab, metadataTab)
        .build()

    private var initialized = false
    override fun init() {
        val layout = BorderLayout(
            width = width,
            height = height,
            direction = BorderLayout.Direction.VERTICAL,
        )
        layout.setFirstElement(topBar) { topBar, width, height -> topBar.width = width }
        val padding = 8
        val gap = 8
        layout.setCenterElement(
            SpreadLayout(
                width = width,
                height = height - 32 * 2,
                padding = Insets(horizonal = padding),
                gap = gap,
            ).apply {
                add(
                    widget = BorderLayout(
                        direction = BorderLayout.Direction.VERTICAL,
                        surface = Surface.listBackgroundWithSeparator(),
                    ).apply {
                        setFirstElement(
                            LinearLayout(
                                direction = LinearLayout.Direction.VERTICAL,
                                padding = Insets(padding),
                                gap = gap,
                            ).apply {
                                val toolbarActions = listOf(
                                    sortButton,
                                    refreshButton,
                                    clearButton,
                                )
                                if (this@ConfigScreen.width >= 700) {
                                    add(
                                        widget = BorderLayout(
                                            height = 20,
                                            direction = BorderLayout.Direction.HORIZONTAL,
                                        ).apply {
                                            setCenterElement(searchBox)
                                            setSecondElement(
                                                LinearLayout(
                                                    direction = LinearLayout.Direction.HORIZONTAL,
                                                    align = LinearLayout.Align.END,
                                                    height = 20,
                                                    gap = gap,
                                                    padding = Insets(left = padding),
                                                ).apply {
                                                    toolbarActions.forEach {
                                                        add(it, Positioner.create().apply { alignVerticalCenter() })
                                                    }
                                                    pack()
                                                }
                                            )
                                        },
                                        expand = true,
                                    )
                                } else {
                                    add(searchBox, expand = true)
                                    add(
                                        widget = LinearLayout(
                                            direction = LinearLayout.Direction.HORIZONTAL,
                                            height = 20,
                                            align = LinearLayout.Align.END,
                                            gap = gap,
                                        ).apply {
                                            toolbarActions.forEach {
                                                add(it, Positioner.create().apply { alignVerticalCenter() })
                                            }
                                        },
                                        expand = true,
                                    )
                                }
                                pack()
                            }
                        )
                        setCenterElement(loadingOverlay)
                        setSecondElement(pager, Positioner.create().margin(padding))
                        addDrawable(this)
                        addDrawable(loadingOverlay)
                    },
                    weight = 2
                )
                add(
                    TabNavigationWrapper(
                        tabManager = tabManager,
                        inner = tabNavigationWidget,
                        surface = Surface.combine(
                            Surface.padding(Insets(bottom = 2), Surface.listBackground()),
                            Surface.footerSeparator(),
                        ),
                    ).also {
                        addDrawableChild(it)
                    }
                )
            }
        )
        layout.setSecondElement(
            LinearLayout(
                width = width,
                height = 32,
                direction = LinearLayout.Direction.HORIZONTAL,
                align = LinearLayout.Align.CENTER,
                gap = gap,
            ).apply {
                add(openModelDirectoryButton, Positioner.create().apply { alignVerticalCenter() })
                add(closeButton, Positioner.create().apply { alignVerticalCenter() })
            }
        )

        layout.refreshPositions()

        val (rows, columns) = modelGrid.calculateSize()
        viewModel.updatePageSize((rows * columns).takeIf { it > 0 })
        modelGrid.forEachChild { remove(it) }

        layout.forEachChild { addDrawableChild(it) }

        pager.init()
        addDrawableChild(pager)
        if (!initialized) {
            initialized = true
            tabNavigationWidget.selectTab(0, false)
        } else {
            tabManager.currentTab?.forEachChild { addDrawableChild(it) }
        }
    }

    override fun tick() {
        viewModel.tick()
    }

    override fun close() {
        modelGrid.forEachChild {
            if (it is AutoCloseable) {
                it.close()
            }
        }
        super.close()
    }
}
