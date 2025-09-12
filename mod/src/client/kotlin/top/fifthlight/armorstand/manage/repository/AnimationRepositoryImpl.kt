package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.manage.AnimationItem
import top.fifthlight.armorstand.util.ModelHash
import top.fifthlight.armorstand.util.bind
import top.fifthlight.armorstand.util.exists
import top.fifthlight.armorstand.util.mapExecuted
import java.nio.file.Path
import java.sql.Connection

class AnimationRepositoryImpl(private val conn: Connection) : AnimationRepository {
    override fun upsert(path: String, name: String, lastChanged: Long, sha256: ModelHash) {
        conn.prepareStatement(
            "MERGE INTO animation(path, name, lastChanged, sha256) KEY(path) VALUES(?, ?, ?, ?)"
        ).bind {
            string(path)
            string(name)
            long(lastChanged)
            bytes(sha256.hash)
        }.use {
            it.executeUpdate()
        }
    }

    override fun exists(path: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM animation WHERE path = ? LIMIT 1").bind {
            string(path)
        }.exists()

    override fun findAll(): List<AnimationItem> =
        conn.prepareStatement("SELECT path, name FROM animation").mapExecuted {
            AnimationItem(
                path = Path.of(getString(1)).normalize(),
                name = getString(2)
            )
        }
}
