package top.fifthlight.armorstand.manage.repository

import top.fifthlight.armorstand.util.ModelHash
import top.fifthlight.armorstand.util.bind
import top.fifthlight.armorstand.util.exists
import java.sql.Connection

class ScanSessionRepositoryImpl(private val conn: Connection) : ScanSessionRepository {
    override fun open() {
        conn.createStatement().apply {
            addBatch("CREATE TEMPORARY TABLE scanned_file_sha256(sha256 BINARY(32) PRIMARY KEY)")
            addBatch("CREATE TEMPORARY TABLE scanned_model_paths(path VARCHAR PRIMARY KEY)")
            addBatch("CREATE TEMPORARY TABLE scanned_marker_model_paths(path VARCHAR PRIMARY KEY)")
            addBatch("CREATE TEMPORARY TABLE scanned_animation_paths(path VARCHAR PRIMARY KEY)")
            addBatch("CREATE TEMPORARY TABLE scanned_thumbnail_sha256(sha256 BINARY(32) PRIMARY KEY)")
            executeBatch()
        }
    }

    override fun close() {
        conn.createStatement().apply {
            addBatch("DROP TABLE scanned_file_sha256")
            addBatch("DROP TABLE scanned_model_paths")
            addBatch("DROP TABLE scanned_marker_model_paths")
            addBatch("DROP TABLE scanned_animation_paths")
            addBatch("DROP TABLE scanned_thumbnail_sha256")
            executeBatch()
        }
    }

    override fun markFileSha(sha256: ModelHash) {
        conn.prepareStatement(
            "MERGE INTO scanned_file_sha256(sha256) KEY(sha256) VALUES(?)"
        ).bind {
            bytes(sha256.hash)
        }.use {
            it.executeUpdate()
        }
    }

    override fun markModelPath(path: String) {
        conn.prepareStatement(
            "MERGE INTO scanned_model_paths(path) KEY(path) VALUES(?)"
        ).bind {
            string(path)
        }.use {
            it.executeUpdate()
        }
    }

    override fun markMarkerModelPath(path: String) {
        conn.prepareStatement(
            "MERGE INTO scanned_marker_model_paths(path) KEY(path) VALUES(?)"
        ).bind {
            string(path)
        }.use {
            it.executeUpdate()
        }
    }

    override fun markAnimationPath(path: String) {
        conn.prepareStatement(
            "MERGE INTO scanned_animation_paths(path) KEY(path) VALUES(?)"
        ).bind {
            string(path)
        }.use {
            it.executeUpdate()
        }
    }

    override fun markThumbnailSha(sha256: ModelHash) {
        conn.prepareStatement(
            "MERGE INTO scanned_thumbnail_sha256(sha256) KEY(sha256) VALUES(?)"
        ).bind {
            bytes(sha256.hash)
        }.use {
            it.executeUpdate()
        }
    }

    override fun isMarkerModelMarked(path: String): Boolean {
        return conn.prepareStatement(
            "SELECT 1 FROM scanned_marker_model_paths WHERE path = ? LIMIT 1"
        ).bind {
            string(path)
        }.exists()
    }

    override fun isThumbnailMarked(sha256: ModelHash): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM scanned_thumbnail_sha256 WHERE sha256 = ? LIMIT 1"
        ).bind {
            bytes(sha256.hash)
        }.exists()

    override fun cleanup() {
        conn.createStatement().use { st ->
            st.addBatch(
                """
                DELETE FROM file
                WHERE NOT EXISTS (
                    SELECT 1 FROM scanned_file_sha256 s WHERE s.sha256 = file.sha256
                )
            """.trimIndent()
            )
            st.addBatch(
                """
                DELETE FROM model
                WHERE NOT EXISTS (
                    SELECT 1 FROM scanned_model_paths s WHERE s.path = model.path
                )
                AND NOT EXISTS (
                    SELECT 1 FROM scanned_marker_model_paths s WHERE s.path = model.path
                )
            """.trimIndent()
            )
            st.addBatch(
                """
                DELETE FROM animation
                WHERE NOT EXISTS (
                    SELECT 1 FROM scanned_animation_paths s WHERE s.path = animation.path
                )
            """.trimIndent()
            )
            st.addBatch(
                """
                DELETE FROM embed_thumbnails
                WHERE NOT EXISTS (
                    SELECT 1 FROM scanned_thumbnail_sha256 s WHERE s.sha256 = embed_thumbnails.sha256
                )
            """.trimIndent()
            )
            st.executeBatch()
        }
    }
}
