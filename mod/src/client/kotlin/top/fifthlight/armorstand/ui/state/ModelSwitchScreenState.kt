package top.fifthlight.armorstand.ui.state

import top.fifthlight.armorstand.manage.model.ModelItem

data class ModelSwitchScreenState(
    val currentTick: Int? = null,
    val lastActiveTick: Int = 0,
    val needToBeClosed: Boolean = false,
    val content: Content = Content.Loading,
) {
    sealed class Content {
        data object Loading : Content()
        data object Empty : Content()
        data class Loaded(
            val currentIndex: Int,
            val totalCount: Int,
            val extraItem: ModelItem?,
            val totalModels: List<ModelItem>,
        ) : Content()
    }
}
