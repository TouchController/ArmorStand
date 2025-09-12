package top.fifthlight.armorstand.manage

import kotlinx.coroutines.flow.StateFlow
import top.fifthlight.armorstand.manage.database.DatabaseManager
import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.armorstand.manage.model.ModelThumbnail
import top.fifthlight.armorstand.util.ModelHash
import java.nio.file.Path
import java.time.Instant

interface ModelManager {
    enum class Order {
        NAME,
        LAST_CHANGED,
    }

    // Only for debugging.
    val databaseManager: DatabaseManager

    fun startWatching()
    fun stopWatching()

    val lastUpdateTime: StateFlow<Instant?>
    fun scheduleScan(immediately: Boolean = false)

    suspend fun getTotalModels(search: String? = null): Int
    suspend fun getModelByPath(path: Path): ModelItem?
    suspend fun getModelByHash(hash: ModelHash): ModelItem?
    suspend fun getAnimations(): List<AnimationItem>
    suspend fun getModelThumbnail(modelItem: ModelItem): ModelThumbnail
    suspend fun getModels(
        offset: Int,
        length: Int,
        search: String? = null,
        order: Order = Order.NAME,
        ascend: Boolean = true,
    ): List<ModelItem>

    suspend fun setFavorite(path: Path, favorite: Boolean)
    suspend fun getFavoriteModels(): List<ModelItem>
    suspend fun getTotalFavoriteModels(): Int
    suspend fun getFavoriteModelIndex(path: Path): Int?
}
