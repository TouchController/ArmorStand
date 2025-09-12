package top.fifthlight.armorstand.manage.schema

import org.slf4j.LoggerFactory
import java.sql.Connection

class H2SchemaManager : SchemaManager {
    private val logger = LoggerFactory.getLogger(H2SchemaManager::class.java)

    private val currentVersion = 3

    // Minimum supported database version. Recreate the table if version smaller than this
    private val minSupportedVersion = 2

    private val upgradeScripts = mapOf(
        // 2 -> 3
        2 to listOf(
            // New table
            """
            CREATE TABLE IF NOT EXISTS favorite(
                path VARCHAR PRIMARY KEY,
                favorite_at BIGINT NOT NULL
            )
            """.trimIndent(),
            // Foreign key
            """
            ALTER TABLE favorite
            ADD CONSTRAINT fk_favorite_model_path
            FOREIGN KEY (path)
            REFERENCES model(path)
            ON DELETE CASCADE
            """.trimIndent(),
            // Indices
            "CREATE INDEX IF NOT EXISTS idx_model_name ON model(name)",
            "CREATE INDEX IF NOT EXISTS idx_model_lastChanged ON model(lastChanged)",
            "CREATE INDEX IF NOT EXISTS idx_favorite_favorite_at ON favorite(favorite_at DESC)"
        )
    )

    override fun maintainSchema(conn: Connection) {
        when (val current = readVersion(conn)) {
            null, !in minSupportedVersion..currentVersion -> {
                logger.info("No valid schema or unsupported version($current), recreating all tables to v$currentVersion")
                recreateAll(conn)
            }

            currentVersion -> {
                logger.info("Schema is up-to-date (v$current)")
            }

            else -> {
                logger.info("Upgrading schema from v$current to v$currentVersion")
                upgrade(conn, current, currentVersion)
            }
        }
    }

    // Read version. If failed to read, return null
    private fun readVersion(conn: Connection): Int? {
        // Check whether version table exists
        conn.prepareStatement(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME)='version'"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                rs.next()
                if (rs.getInt(1) < 1) {
                    return null
                }
            }
        }

        // Read version
        return conn.prepareStatement("SELECT version FROM version").use { ps ->
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getInt("version")
                } else {
                    null
                }
            }
        }
    }

    // Do a full recreate
    private fun recreateAll(conn: Connection) {
        conn.createStatement().use { statement ->
            // Drop all known tables
            statement.addBatch("DROP TABLE IF EXISTS version")
            statement.addBatch("DROP TABLE IF EXISTS file")
            statement.addBatch("DROP TABLE IF EXISTS model")
            statement.addBatch("DROP TABLE IF EXISTS animation")
            statement.addBatch("DROP TABLE IF EXISTS embed_thumbnails")
            statement.addBatch("DROP TABLE IF EXISTS favorite")

            // Create version table
            statement.addBatch("CREATE TABLE version (version INTEGER)")

            // Data tables
            statement.addBatch(
                """
                CREATE TABLE file(
                  path VARCHAR PRIMARY KEY,
                  lastChanged BIGINT NOT NULL,
                  sha256 BINARY(32) NOT NULL
                )
            """.trimIndent()
            )
            statement.addBatch(
                """
                CREATE TABLE model(
                  path VARCHAR PRIMARY KEY,
                  name VARCHAR NOT NULL,
                  lastChanged BIGINT NOT NULL,
                  sha256 BINARY(32) NOT NULL
                )
            """.trimIndent()
            )
            statement.addBatch(
                """
                CREATE TABLE animation(
                  path VARCHAR PRIMARY KEY,
                  name VARCHAR NOT NULL,
                  lastChanged BIGINT NOT NULL,
                  sha256 BINARY(32) NOT NULL
                )
            """.trimIndent()
            )
            statement.addBatch(
                """
                CREATE TABLE embed_thumbnails(
                  sha256 BINARY(32) PRIMARY KEY,
                  fileOffset BIGINT NOT NULL,
                  fileLength BIGINT NOT NULL,
                  mimeType VARCHAR
                )
            """.trimIndent()
            )
            statement.addBatch(
                """
                CREATE TABLE favorite(
                  path VARCHAR PRIMARY KEY,
                  favorite_at BIGINT NOT NULL
                )
            """.trimIndent()
            )
            statement.addBatch(
                """
                ALTER TABLE favorite
                ADD CONSTRAINT fk_favorite_model_path
                FOREIGN KEY(path) REFERENCES model(path)
                ON DELETE CASCADE
            """.trimIndent()
            )

            // Indices
            statement.addBatch("CREATE INDEX idx_model_name ON model(name)")
            statement.addBatch("CREATE INDEX idx_model_lastChanged ON model(lastChanged)")
            statement.addBatch("CREATE INDEX idx_favorite_favorite_at ON favorite(favorite_at DESC)")
            statement.executeBatch()
        }

        // Insert version
        conn.prepareStatement("INSERT INTO version(version) VALUES(?)").use { ps ->
            ps.setInt(1, currentVersion)
            ps.executeUpdate()
        }

        logger.info("Recreated schema to version $currentVersion")
    }

    // Upgrade database to specified version
    private fun upgrade(conn: Connection, from: Int, to: Int) {
        for (v in from until to) {
            val scripts = upgradeScripts[v]
                ?: error("No upgrade scripts defined for version $v")
            conn.createStatement().use { st ->
                scripts.forEach { st.addBatch(it) }
                st.executeBatch()
            }
        }
        // Update version table after all scripts are executed
        conn.createStatement().use { st ->
            st.addBatch("DELETE FROM version")
            st.addBatch("INSERT INTO version(version) VALUES($to)")
            st.executeBatch()
        }
        logger.info("Upgraded schema from v$from to v$to")
    }
}
