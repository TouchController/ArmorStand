package top.fifthlight.armorstand.ui.state

import top.fifthlight.armorstand.manage.ModelManager
import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.blazerod.model.Metadata
import java.nio.file.Path

data class ConfigScreenState(
    val currentModel: Path? = null,
    val currentMetadata: Metadata? = null,
    val showOtherPlayerModel: Boolean = true,
    val sendModelData: Boolean = true,
    val hidePlayerShadow: Boolean = false,
    val modelScale: Float = 1f,
    val thirdPersonDistanceScale: Float = 1f,
    val currentModelMetadata: Metadata? = null,
    val searchString: String = "",
    val order: ModelManager.Order = ModelManager.Order.NAME,
    val sortAscend: Boolean = true,
    val pageSize: Int? = null,
    val currentOffset: Int = 0,
    val totalItems: Int = 0,
    val currentPageItems: List<ModelItem>? = null,
) {
    init {
        pageSize?.let {
            require(pageSize > 0) { "Page size not greater than 0" }
        }
    }
}
