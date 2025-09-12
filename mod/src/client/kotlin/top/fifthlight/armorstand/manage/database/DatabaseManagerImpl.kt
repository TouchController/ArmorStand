package top.fifthlight.armorstand.manage.database

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import top.fifthlight.armorstand.manage.repository.*
import top.fifthlight.armorstand.manage.schema.SchemaManager
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors

class DatabaseManagerImpl : DatabaseManager {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseManagerImpl::class.java)
    }

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Model database thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var connection: Pair<Connection, TransactionScope>? = null

    private class TransactionScopeImpl(
        override val connection: Connection,
    ) : TransactionScope {
        override val modelRepository by lazy {
            ModelRepositoryImpl(connection)
        }

        override val animationRepository by lazy {
            AnimationRepositoryImpl(connection)
        }

        override val favoriteRepository by lazy {
            FavoriteRepositoryImpl(connection)
        }

        override val thumbRepository by lazy {
            ThumbnailRepositoryImpl(connection)
        }

        override val fileCacheRepository by lazy {
            FileCacheRepositoryImpl(connection)
        }

        override val scanSessionRepository by lazy {
            ScanSessionRepositoryImpl(connection)
        }
    }

    override suspend fun start(
        driverClass: String,
        jdbcUrl: String,
        schemaManager: SchemaManager,
    ) = withContext(dispatcher) {
        Class.forName(driverClass)
        val newConnection = DriverManager.getConnection(jdbcUrl).apply {
            autoCommit = false
            isReadOnly = false
        }
        connection = Pair(newConnection, TransactionScopeImpl(newConnection))
        logger.info("Opened model database")
        try {
            schemaManager.maintainSchema(newConnection)
        } catch (ex: Exception) {
            try {
                newConnection.rollback()
            } catch (rollbackEx: Exception) {
                rollbackEx.addSuppressed(ex)
                throw rollbackEx
            }
            throw ex
        }
    }

    override suspend fun stop() {
        withContext(dispatcher) {
            connection?.first?.close()
        }
        dispatcher.close()
        logger.info("Closed model database")
    }

    override suspend fun <T> transaction(block: suspend TransactionScope.() -> T) = checkNotNull(connection) {
        "Database connection is not initialized"
    }.let { (connection, scope) ->
        withContext(dispatcher) {
            try {
                val result = block(scope)
                connection.commit()
                result
            } catch (ex: Exception) {
                try {
                    connection.rollback()
                } catch (rollbackEx: Exception) {
                    rollbackEx.addSuppressed(ex)
                    throw rollbackEx
                }
                throw ex
            }
        }
    }
}
