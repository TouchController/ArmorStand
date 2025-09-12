package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.manage.model.ModelItem

interface FavoriteRepository {
    fun setFavorite(path: String, favorite: Boolean, timeMillis: Long)
    fun findAll(): List<ModelItem>
    fun count(): Int
    fun rankIndex(path: String): Int?
}
