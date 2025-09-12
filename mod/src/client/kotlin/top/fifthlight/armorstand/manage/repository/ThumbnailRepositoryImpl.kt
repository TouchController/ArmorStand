package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.manage.model.ModelThumbnail
import top.fifthlight.armorstand.util.ModelHash
import top.fifthlight.armorstand.util.bind
import top.fifthlight.armorstand.util.exists
import top.fifthlight.armorstand.util.firstExecuted
import top.fifthlight.blazerod.model.Texture
import java.sql.Connection

class ThumbnailRepositoryImpl(private val conn: Connection) : ThumbnailRepository {
    override fun existsEmbed(sha256: ModelHash): Boolean =
        conn.prepareStatement("SELECT 1 FROM embed_thumbnails WHERE sha256 = ? LIMIT 1").bind {
            bytes(sha256.hash)
        }.exists()

    override fun insertEmbed(sha256: ModelHash, offset: Long, length: Long, mimeType: String?) {
        conn.prepareStatement(
            "INSERT INTO embed_thumbnails(sha256, fileOffset, fileLength, mimeType) VALUES(?, ?, ?, ?)"
        ).bind {
            bytes(sha256.hash)
            long(offset)
            long(length)
            string(mimeType)
        }.use {
            it.executeUpdate()
        }
    }

    override fun findEmbed(sha256: ModelHash): ModelThumbnail =
        conn.prepareStatement(
            "SELECT fileOffset, fileLength, mimeType FROM embed_thumbnails WHERE sha256 = ? LIMIT 1"
        ).bind {
            bytes(sha256.hash)
        }.firstExecuted {
            val mime = getString(3)
            ModelThumbnail.Embed(
                offset = getLong(1),
                length = getLong(2),
                type = Texture.TextureType.entries.firstOrNull { it.mimeType == mime },
            )
        } ?: ModelThumbnail.None
}
