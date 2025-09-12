package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.manage.model.ModelItem
import top.fifthlight.armorstand.util.*
import java.nio.file.Path
import java.sql.Connection

class FavoriteRepositoryImpl(private val conn: Connection) : FavoriteRepository {
    override fun setFavorite(path: String, favorite: Boolean, timeMillis: Long) {
        if (favorite) {
            conn.prepareStatement(
                "MERGE INTO favorite(path, favorite_at) KEY(path) VALUES(?, ?)"
            ).bind {
                string(path)
                long(timeMillis)
            }.use {
                it.executeUpdate()
            }
        } else {
            conn.prepareStatement("DELETE FROM favorite WHERE path = ?").bind {
                string(path)
            }.use {
                it.executeUpdate()
            }
        }
    }

    override fun findAll(): List<ModelItem> =
        conn.prepareStatement(
            """
            SELECT m.path, m.name, m.lastChanged, m.sha256, TRUE
            FROM model m
              INNER JOIN favorite f ON m.path = f.path
            ORDER BY f.favorite_at DESC
            """.trimIndent()
        ).mapExecuted {
            ModelItem(
                path = Path.of(getString(1)).normalize(),
                name = getString(2),
                lastChanged = getLong(3),
                hash = ModelHash(getBytes(4)),
                favorite = true
            )
        }

    override fun count(): Int =
        conn.prepareStatement("SELECT COUNT(*) FROM favorite").count()

    override fun rankIndex(path: String): Int? =
        conn.prepareStatement(
            """
            SELECT ranked.rank_num
            FROM (
              SELECT m.path,
                     ROW_NUMBER() OVER (ORDER BY f.favorite_at DESC) AS rank_num
              FROM model m
                INNER JOIN favorite f ON m.path = f.path
            ) ranked
            WHERE ranked.path = ?
            """.trimIndent()
        ).bind {
            string(path)
        }.firstExecuted {
            getInt(1) - 1
        }
}
