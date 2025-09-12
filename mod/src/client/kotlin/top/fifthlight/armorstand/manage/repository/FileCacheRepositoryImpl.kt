package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.util.bind
import top.fifthlight.armorstand.util.firstExecuted
import java.sql.Connection

class FileCacheRepositoryImpl(private val conn: Connection) : FileCacheRepository {
    override fun findSha256(path: String, lastChanged: Long): ByteArray? =
        conn.prepareStatement(
            "SELECT sha256 FROM file WHERE path = ? AND lastChanged = ? LIMIT 1"
        ).bind {
            string(path)
            long(lastChanged)
        }.firstExecuted {
            getBytes(1)
        }

    override fun upsertCache(path: String, lastChanged: Long, sha256: ByteArray) {
        conn.prepareStatement(
            "MERGE INTO file(path, lastChanged, sha256) KEY(path) VALUES(?, ?, ?)"
        ).bind {
            string(path)
            long(lastChanged)
            bytes(sha256)
        }.use {
            it.executeUpdate()
        }
    }
}
