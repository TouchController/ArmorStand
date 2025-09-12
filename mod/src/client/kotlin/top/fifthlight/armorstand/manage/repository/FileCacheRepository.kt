package top.fifthlight.armorstand.manage.repository

interface FileCacheRepository {
    fun findSha256(path: String, lastChanged: Long): ByteArray?
    fun upsertCache(path: String, lastChanged: Long, sha256: ByteArray)
}
