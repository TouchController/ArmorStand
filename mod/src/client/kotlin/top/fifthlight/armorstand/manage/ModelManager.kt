package top.fifthlight.armorstand.manage

import com.mojang.logging.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.util.Identifier
import top.fifthlight.armorstand.ArmorStand
import top.fifthlight.armorstand.ArmorStandClient
import top.fifthlight.armorstand.state.ModelInstanceManager
import top.fifthlight.armorstand.util.*
import top.fifthlight.blazerod.model.ModelFileLoader
import top.fifthlight.blazerod.model.ModelFileLoaders
import top.fifthlight.blazerod.model.Texture
import top.fifthlight.blazerod.util.ObjectPool
import top.fifthlight.blazerod.util.Pool
import top.fifthlight.blazerod.util.ThreadSafeObjectPool
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import kotlin.io.path.*
import kotlin.time.measureTime

object ModelManager {
    private val LOGGER = LogUtils.getLogger()
    val modelDir: Path
        get() = ModelInstanceManager.modelDir
    private const val DEFAULT_MODEL_NAME = "armorstand.vrm"
    private const val DATABASE_NAME = ".cache"
    private val databaseFile = modelDir.resolve("$DATABASE_NAME.mv.db").toAbsolutePath()
    private const val DATABASE_VERSION = 3
    var connectionPool: Pool<Connection>? = null
        private set

    inline fun <T> transaction(crossinline block: Connection.() -> T): T {
        val connectionPool = connectionPool ?: error("Database is not connected")
        return connectionPool.transaction(block)
    }

    private fun Connection.checkVersion(): Int? {
        query("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'version';").use { result ->
            result.skipToInitialRow()
            if (result.getInt(1) < 1) {
                LOGGER.info("No version item in database, create new tables")
                return null
            }
        }
        return query("SELECT version FROM version;").use { result ->
            result.skipToInitialRow()
            val version = result.getInt(1)
            LOGGER.info("Database version {}", version)
            version
        }
    }

    private val upgradeStatements = mapOf(
        2 to listOf(
            """
            CREATE TABLE IF NOT EXISTS favorite(
                path VARCHAR PRIMARY KEY,
                favorite_at BIGINT NOT NULL
            );
            """,
            """
            ALTER TABLE favorite
            ADD CONSTRAINT fk_favorite_model_path
            FOREIGN KEY (path)
            REFERENCES model (path)
            ON DELETE CASCADE;
            """,
            """
            CREATE INDEX idx_model_name ON model (name);
            CREATE INDEX idx_model_lastChanged ON model (lastChanged);
            CREATE INDEX idx_favorite_favorite_at ON favorite (favorite_at DESC);
            """
        )
    )

    private suspend fun waitUntilFirstScan(): Instant {
        if (_lastScanTime.value == null) {
            scheduleScan()
        }
        return _lastScanTime.mapNotNull { it }.first()
    }

    suspend fun getTotalModels(
        searchString: String? = null,
    ): Int {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                if (searchString == null) {
                    query("SELECT COUNT(*) FROM model")
                } else {
                    prepareQuery("SELECT COUNT(*) FROM model WHERE LOCATE(LOWER(?), LOWER(name)) > 0") {
                        setString(1, searchString)
                    }
                }.use { result ->
                    result.skipToInitialRow()
                    result.getInt(1)
                }
            }
        }
    }

    enum class Order {
        NAME,
        LAST_CHANGED,
    }

    private fun ResultSet.readModelItem() = ModelItem(
        path = Path.of(getString(1)).normalize(),
        name = getString(2),
        lastChanged = getLong(3),
        hash = ModelHash(getBytes(4)),
        favorite = getBoolean(5),
    )

    suspend fun getModel(
        offset: Int,
        length: Int,
        searchString: String? = null,
        order: Order = Order.NAME,
        ascend: Boolean = true,
    ): List<ModelItem> {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                val orderColumn = when (order) {
                    Order.NAME -> "m.name"
                    Order.LAST_CHANGED -> "m.lastChanged"
                }
                val sortDirection = if (ascend) {
                    "ASC"
                } else {
                    "DESC"
                }
                val searchQueryClause = searchString?.let { "WHERE LOCATE(LOWER(?), LOWER(m.name)) > 0" } ?: ""
                prepareQuery(
                    """
                    SELECT
                        m.path, m.name, m.lastChanged, m.sha256, f.path IS NOT NULL AS isFavorite
                    FROM
                        model m
                    LEFT JOIN
                        favorite f ON m.path = f.path
                    $searchQueryClause
                    ORDER BY
                        CASE WHEN f.path IS NOT NULL THEN 0 ELSE 1 END,
                        CASE WHEN f.path IS NOT NULL THEN f.favorite_at END DESC,
                        $orderColumn $sortDirection
                    LIMIT ? OFFSET ?
                    """
                ) {
                    var nextParam = 1
                    searchString?.let { setString(nextParam++, it) }
                    setInt(nextParam++, length)
                    setInt(nextParam++, offset)
                }.use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.readModelItem())
                        }
                    }
                }
            }
        }
    }

    suspend fun getModel(path: Path): ModelItem? {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                prepareQuery(
                    """
                    SELECT
                        m.path, m.name, m.lastChanged, m.sha256, f.path IS NOT NULL AS isFavorite
                    FROM
                        model m
                    LEFT JOIN favorite f ON m.path = f.path
                    WHERE m.path = ?
                    """
                ) {
                    setString(1, path.normalize().toString())
                }.use { result ->
                    if (result.next()) {
                        result.readModelItem()
                    } else {
                        null
                    }
                }
            }
        }
    }

    suspend fun getModel(modelHash: ModelHash): ModelItem? {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                prepareQuery(
                    """
                    SELECT
                        m.path, m.name, m.lastChanged, m.sha256, f.path IS NOT NULL AS isFavorite
                    FROM
                        model m
                    LEFT JOIN favorite f ON m.path = f.path
                    WHERE sha256 = ?
                    """
                ) {
                    setBytes(1, modelHash.hash)
                }.use { result ->
                    if (result.next()) {
                        result.readModelItem()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun ResultSet.readAnimationItem() = AnimationItem(
        path = Path.of(getString(1)).normalize(),
        name = getString(2),
    )

    suspend fun getAnimations(): List<AnimationItem> {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                query("SELECT path, name FROM animation").use { result ->
                    buildList {
                        while (result.next()) {
                            val animationItem = result.readAnimationItem()
                            add(animationItem)
                        }
                    }
                }
            }
        }
    }

    sealed class ModelThumbnail {
        data object None : ModelThumbnail()

        data class Embed(
            val offset: Long,
            val length: Long,
            val type: Texture.TextureType? = null,
        ) : ModelThumbnail()
    }

    suspend fun getModelThumbnail(modelItem: ModelItem): ModelThumbnail {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                prepareQuery("SELECT fileOffset, fileLength, mimeType FROM embed_thumbnails WHERE sha256 = ? LIMIT 1") {
                    setBytes(1, modelItem.hash.hash)
                }.use { result ->
                    if (!result.next()) {
                        return@use ModelThumbnail.None
                    }
                    ModelThumbnail.Embed(
                        offset = result.getLong(1),
                        length = result.getLong(2),
                        type = result.getString(3)?.let { type ->
                            Texture.TextureType.entries.firstOrNull { it.mimeType == type }
                        },
                    )
                }
            }
        }
    }

    suspend fun setFavoriteModel(path: Path, favorite: Boolean) = withContext(Dispatchers.IO) {
        transaction {
            if (favorite) {
                prepare("MERGE INTO favorite (path, favorite_at) KEY (path) VALUES (?, ?)") {
                    setString(1, path.normalize().toString())
                    setLong(2, System.currentTimeMillis())
                }
            } else {
                prepare("DELETE FROM favorite WHERE path = ?") {
                    setString(1, path.normalize().toString())
                }
            }
            _lastScanTime.getAndUpdate { it?.let { Instant.now() } }
        }
    }

    suspend fun getFavoriteModels(): List<ModelItem> {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                query(
                    """
                    SELECT
                        m.path, m.name, m.lastChanged, m.sha256, TRUE AS isFavorite
                    FROM
                        model m
                    INNER JOIN
                        favorite f ON m.path = f.path
                    ORDER BY f.favorite_at DESC
                    """
                ).use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.readModelItem())
                        }
                    }
                }
            }
        }
    }

    suspend fun getTotalFavoriteModels(): Int {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                query(
                    """
                    SELECT
                        COUNT(*)
                    FROM
                        model m
                    INNER JOIN
                        favorite f ON m.path = f.path
                    """
                ).use { result ->
                    result.skipToInitialRow()
                    result.getInt(1)
                }
            }
        }
    }

    suspend fun getFavoriteModelIndex(path: Path): Int? {
        waitUntilFirstScan()
        return withContext(Dispatchers.IO) {
            transaction {
                prepareQuery(
                    """
                    SELECT ranked.rank_num
                    FROM (
                        SELECT
                            m.path,
                            ROW_NUMBER() OVER (ORDER BY f.favorite_at DESC) AS rank_num
                        FROM
                            model m
                        INNER JOIN
                            favorite f ON m.path = f.path
                    ) AS ranked
                    WHERE ranked.path = ?;
                    """
                ) {
                    setString(1, path.normalize().toString())
                }.use { result ->
                    if (result.next()) {
                        result.getInt(1) - 1
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun calculateSha256(path: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileChannel.open(path).use { channel ->
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (true) {
                buffer.clear()
                if (channel.read(buffer) == -1) {
                    break
                }
                buffer.flip()
                digest.update(buffer)
            }
        }
        return digest.digest()
    }

    private val _lastScanTime = MutableStateFlow<Instant?>(null)
    val lastScanTime = _lastScanTime.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun doScan() = withContext(Dispatchers.IO) {
        transaction {
            execute("DROP TABLE IF EXISTS scanned_file_sha256;")
            execute(
                """
                    CREATE TEMPORARY TABLE scanned_file_sha256(
                        sha256 BINARY(32) PRIMARY KEY
                    );
                """
            )
            execute("DROP TABLE IF EXISTS scanned_model_paths;")
            execute(
                """
                    CREATE TEMPORARY TABLE scanned_model_paths(
                        path VARCHAR PRIMARY KEY
                    );
                """
            )
            execute("DROP TABLE IF EXISTS scanned_animation_paths;")
            execute(
                """
                CREATE TEMPORARY TABLE scanned_animation_paths(
                    path VARCHAR PRIMARY KEY
                );
                """
            )
            execute("DROP TABLE IF EXISTS scanned_thumbnail_sha256;")
            execute(
                """
                    CREATE TEMPORARY TABLE scanned_thumbnail_sha256(
                        sha256 BINARY(32) PRIMARY KEY
                    );
                """
            )
        }

        fun processFile(relativePath: Path) {
            val extension = relativePath.extension.takeIf { it.isNotEmpty() }?.lowercase()
            val pathStr = relativePath.normalize().toString()
            val name = relativePath.fileName.toString()
            val path = modelDir.resolve(relativePath)
            val lastChanged = path.getLastModifiedTime().toMillis()
            transaction {
                LOGGER.trace("Process file {}", relativePath)
                prepare("INSERT INTO scanned_model_paths (path) VALUES (?)") {
                    setString(1, pathStr)
                }

                val sha256 = prepareQuery("SELECT sha256 FROM file WHERE path = ? AND lastChanged = ? LIMIT 1;") {
                    setString(1, pathStr)
                    setLong(2, lastChanged)
                }.use { result ->
                    if (result.next()) {
                        LOGGER.trace("File already in file cache, skip calculating sha256.")
                        val sha256 = result.getBytes(1)
                        sha256
                    } else {
                        val sha256 = calculateSha256(path)
                        prepare("INSERT INTO file (path, lastChanged, sha256) VALUES (?, ?, ?)") {
                            setString(1, pathStr)
                            setLong(2, lastChanged)
                            setBytes(3, sha256)
                        }
                        LOGGER.trace("SHA-256 for file {} is {}", relativePath, sha256.toHexString())
                        sha256
                    }
                }
                prepare("MERGE INTO scanned_file_sha256 (sha256) KEY (sha256) VALUES (?)") {
                    setBytes(1, sha256)
                }

                if (extension in ModelLoaders.modelExtensions) {
                    prepare("MERGE INTO scanned_model_paths (path) KEY (path) VALUES (?)") {
                        setString(1, pathStr)
                    }
                    prepareQuery("SELECT COUNT(*) FROM model WHERE path = ? LIMIT 1;") {
                        setString(1, pathStr)
                    }.use { result ->
                        result.skipToInitialRow()
                        if (result.getInt(1) > 0) {
                            LOGGER.trace("Already scanned model, skip processing model.")
                            prepare("MERGE INTO scanned_thumbnail_sha256 (sha256) KEY (sha256) VALUES (?)") {
                                setBytes(1, sha256)
                            }
                            return@use
                        }

                        prepare("DELETE FROM model WHERE path = ?") {
                            setString(1, pathStr)
                        }
                        prepare("INSERT INTO model (path, name, lastChanged, sha256) VALUES (?, ?, ?, ?)") {
                            setString(1, pathStr)
                            setString(2, name)
                            setLong(3, lastChanged)
                            setBytes(4, sha256)
                        }

                        if (extension in ModelLoaders.embedThumbnailExtensions) {
                            prepareQuery("SELECT COUNT(*) FROM embed_thumbnails WHERE sha256 = ? LIMIT 1;") {
                                setBytes(1, sha256)
                            }.use { result ->
                                result.skipToInitialRow()
                                if (result.getInt(1) > 0) {
                                    LOGGER.trace("Already scanned embed thumbnails, skip.")
                                    return@use
                                }
                                prepareQuery("SELECT COUNT(*) FROM scanned_thumbnail_sha256 WHERE sha256 = ? LIMIT 1;") {
                                    setBytes(1, sha256)
                                }.use { result ->
                                    result.skipToInitialRow()
                                    if (result.getInt(1) > 0) {
                                        return@use
                                    }

                                    prepare("INSERT INTO scanned_thumbnail_sha256 (sha256) VALUES (?)") {
                                        setBytes(1, sha256)
                                    }
                                    val thumbnailResult = try {
                                        ModelFileLoaders.getEmbedThumbnail(path)
                                    } catch (ex: Exception) {
                                        LOGGER.warn("Failed to extract thumbnail: {}", pathStr)
                                        null
                                    }
                                    LOGGER.trace("Thumbnail: {}", thumbnailResult)

                                    if (thumbnailResult is ModelFileLoader.ThumbnailResult.Embed) {
                                        prepare("INSERT INTO embed_thumbnails (sha256, fileOffset, fileLength, mimeType) VALUES (?, ?, ?, ?)") {
                                            setBytes(1, sha256)
                                            setLong(2, thumbnailResult.offset)
                                            setLong(3, thumbnailResult.length)
                                            setString(4, thumbnailResult.type?.mimeType)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (extension in ModelLoaders.animationExtensions) {
                    prepare("MERGE INTO scanned_animation_paths (path) KEY (path) VALUES (?)") {
                        setString(1, pathStr)
                    }
                    prepareQuery("SELECT COUNT(*) FROM animation WHERE path = ? LIMIT 1;") {
                        setString(1, pathStr)
                    }.use { result ->
                        result.skipToInitialRow()
                        if (result.getInt(1) > 0) {
                            LOGGER.trace("Already scanned animation, skip processing animation.")
                            return@use
                        }

                        prepare("DELETE FROM animation WHERE path = ?") {
                            setString(1, pathStr)
                        }
                        prepare("INSERT INTO animation (path, name, lastChanged, sha256) VALUES (?, ?, ?, ?)") {
                            setString(1, pathStr)
                            setString(2, name)
                            setLong(3, lastChanged)
                            setBytes(4, sha256)
                        }
                    }
                }
            }
        }

        val time = measureTime {
            coroutineScope {
                val processDispatcher = Dispatchers.IO.limitedParallelism(4)
                Files.walkFileTree(modelDir, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (file.extension in ModelLoaders.scanExtensions) {
                            launch {
                                withContext(processDispatcher) {
                                    try {
                                        processFile(file.toAbsolutePath().relativeTo(modelDir))
                                    } catch (ex: Exception) {
                                        LOGGER.warn("Failed to process file $file", ex)
                                    }
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        LOGGER.info("Failed to visit path $file", exc)
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        }

        transaction {
            execute(
                """
                    DELETE FROM file
                    WHERE NOT EXISTS
                    (SELECT 1 FROM scanned_file_sha256 WHERE scanned_file_sha256.sha256 = file.sha256)
                """
            )
            execute(
                """
                DELETE FROM model
                WHERE NOT EXISTS
                (SELECT 1 FROM scanned_model_paths WHERE scanned_model_paths.path = model.path)
                """
            )
            execute(
                """
                    DELETE FROM animation
                    WHERE NOT EXISTS
                    (SELECT 1 FROM scanned_animation_paths WHERE scanned_animation_paths.path = animation.path)
                """
            )
            execute(
                """
                    DELETE FROM embed_thumbnails
                    WHERE NOT EXISTS
                    (SELECT 1 FROM scanned_thumbnail_sha256 WHERE scanned_thumbnail_sha256.sha256 = embed_thumbnails.sha256)
                """
            )
            execute("DROP TABLE scanned_file_sha256")
            execute("DROP TABLE scanned_model_paths")
            execute("DROP TABLE scanned_animation_paths")
            execute("DROP TABLE scanned_thumbnail_sha256")
        }

        _lastScanTime.value = Instant.now()
        LOGGER.info("Finish scanning models, took $time")
    }

    private var scanning = false
    private var scanScheduled = false
    private val scanLock = Mutex()
    private val scanScope = ArmorStand.instance.scope + Dispatchers.IO
    suspend fun scheduleScan(): Unit = scanLock.withLock {
        if (scanning) {
            scanScheduled = true
            return
        }
        scanScheduled = false
        scanning = true
        scanScope.launch {
            try {
                doScan()
                scanLock.withLock {
                    if (scanScheduled) {
                        scanScheduled = false
                        launch {
                            scheduleScan()
                        }
                    }
                    scanning = false
                }
            } catch (ex: Exception) {
                scanLock.withLock {
                    scanning = false
                    scanScheduled = false
                }
                LOGGER.warn("Failed to scan model", ex)
            }
        }
    }

    private fun listenModelDir(scope: CoroutineScope) = scope.launch {
        withContext(Dispatchers.IO) {
            var watcher: WatchService? = null
            var watchKey: WatchKey? = null
            try {
                watcher = FileSystems.getDefault().newWatchService().also { watcher ->
                    modelDir.register(
                        watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                    )
                }
                while (true) {
                    watchKey = watcher.take()
                    for (event in watchKey.pollEvents()) {
                        when (event.kind()) {
                            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE -> Unit
                            else -> continue
                        }

                        val file = event.context() as Path
                        if (file == databaseFile.relativeTo(modelDir)) {
                            continue
                        }

                        val extension = file.extension
                        if (extension !in ModelLoaders.scanExtensions) {
                            continue
                        }

                        scheduleScan()
                        break
                    }
                    if (!watchKey.reset()) {
                        break
                    }
                }
            } catch (ex: Exception) {
                LOGGER.warn("Failed to watch model directory", ex)
            } finally {
                watchKey?.cancel()
                watcher?.close()
            }
        }
    }

    private fun Connection.createTables() {
        transaction {
            val version = checkVersion()
            when (version) {
                null, !in 2..DATABASE_VERSION -> {
                    execute("DROP TABLE IF EXISTS version;")
                    execute("DROP TABLE IF EXISTS file;")
                    execute("DROP TABLE IF EXISTS model;")
                    execute("DROP TABLE IF EXISTS animation;")
                    execute("DROP TABLE IF EXISTS embed_thumbnails;")
                    execute("DROP TABLE IF EXISTS favorite;")
                    execute("CREATE TABLE version (version INTEGER);")
                    prepare("INSERT INTO version (version) VALUES (?);") { setInt(1, DATABASE_VERSION) }
                    execute(
                        """
                                CREATE TABLE file(
                                    path VARCHAR PRIMARY KEY,
                                    lastChanged BIGINT NOT NULL,
                                    sha256 BINARY(32) NOT NULL
                                );
                                """
                    )
                    execute(
                        """
                                CREATE TABLE model(
                                    path VARCHAR PRIMARY KEY,
                                    name VARCHAR NOT NULL,
                                    lastChanged BIGINT NOT NULL,
                                    sha256 BINARY(32) NOT NULL
                                );                    
                                """
                    )
                    execute(
                        """
                                CREATE TABLE animation(
                                    path VARCHAR PRIMARY KEY,
                                    name VARCHAR NOT NULL,
                                    lastChanged BIGINT NOT NULL,
                                    sha256 BINARY(32) NOT NULL
                                );
                                """
                    )
                    execute(
                        """
                                CREATE TABLE embed_thumbnails(
                                    sha256 BINARY(32) PRIMARY KEY,
                                    fileOffset BIGINT NOT NULL,
                                    fileLength BIGINT NOT NULL,
                                    mimeType VARCHAR
                                );
                                """
                    )
                    execute(
                        """
                                CREATE TABLE favorite(
                                    path VARCHAR PRIMARY KEY,
                                    favorite_at BIGINT NOT NULL
                                );
                                """
                    )
                    execute(
                        """
                        ALTER TABLE favorite
                        ADD CONSTRAINT fk_favorite_model_path
                        FOREIGN KEY (path)
                        REFERENCES model (path)
                        ON DELETE CASCADE;
                        """
                    )
                    execute("CREATE INDEX idx_model_name ON model (name);")
                    execute("CREATE INDEX idx_model_lastChanged ON model (lastChanged);")
                    execute("CREATE INDEX idx_favorite_favorite_at ON favorite (favorite_at DESC);")
                    LOGGER.info("Recreated tables")
                }

                DATABASE_VERSION -> {
                    LOGGER.info("Version match, not upgrade needed")
                }

                else -> {
                    (2 until DATABASE_VERSION)
                        .asSequence()
                        .map { upgradeStatements[it] ?: error("No upgrade statement for version $it") }
                        .forEach { statements ->
                            statements.forEach { statement ->
                                execute(statement)
                            }
                        }
                    execute("DROP TABLE IF EXISTS version;")
                    execute("CREATE TABLE version (version INTEGER);")
                    prepare("INSERT INTO version (version) VALUES (?);") { setInt(1, DATABASE_VERSION) }
                    LOGGER.info("Upgraded from version $version to version $DATABASE_VERSION")
                }
            }
        }
    }

    suspend fun setupModelDirectory(extractDefaultModel: Boolean) = withContext(Dispatchers.IO) {
        if (extractDefaultModel) {
            try {
                LOGGER.info("Extracting default model: {}", DEFAULT_MODEL_NAME)
                javaClass.classLoader.getResourceAsStream(DEFAULT_MODEL_NAME).use { input ->
                    modelDir.resolve(DEFAULT_MODEL_NAME).outputStream().use { output ->
                        input.transferTo(output)
                    }
                }
                LOGGER.info("Extracted default model")
            } catch (ex: Exception) {
                LOGGER.warn("Failed to extract default model", ex)
            }
        }
        if (extractDefaultModel || ArmorStandClient.debug) {
            val defaultAnimationDir = ModelInstanceManager.defaultAnimationDir
            val extractDefaultAnimations = defaultAnimationDir.notExists()
            defaultAnimationDir.createDirectories()
            if (extractDefaultAnimations) {
                LOGGER.info("Extracting default animations")
                val zipFileSystem = try {
                    val uri = this.javaClass.classLoader.getResource("default-animation.zip")!!.toURI()
                    val zipPath = uri.toPath()
                    FileSystems.newFileSystem(zipPath)
                } catch (ex: Exception) {
                    LOGGER.warn("Failed to extract default animations", ex)
                    return@withContext
                }
                zipFileSystem.getPath("/").forEachDirectoryEntry { entry ->
                    if (!entry.isRegularFile()) {
                        return@forEachDirectoryEntry
                    }
                    val targetPath = defaultAnimationDir.resolve(entry.fileName.toString())
                    try {
                        Files.newByteChannel(entry, StandardOpenOption.READ).use { source ->
                            Files.newByteChannel(
                                targetPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                            ).use { target ->
                                source.copyTo(target)
                            }
                        }
                    } catch (ex: Exception) {
                        LOGGER.warn("Failed to extract animation file $entry", ex)
                    }
                }
                LOGGER.info("Extracted default animations")
            }
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val extractDefaultModel = modelDir.notExists()
        modelDir.createDirectories()
        setupModelDirectory(extractDefaultModel)

        Class.forName("org.h2.Driver")
        val databaseRelativePath = databaseFile.relativeTo(Paths.get(".").toAbsolutePath())
        val jdbcUrl = "jdbc:h2:./${databaseRelativePath.toString().removeSuffix(".mv.db")}"
        val connectionPool = ThreadSafeObjectPool(
            ObjectPool<Connection>(
                identifier = Identifier.of("armorstand", "database_connection"),
                create = {
                    DriverManager.getConnection(jdbcUrl)
                },
                onAcquired = {
                    autoCommit = false
                    isReadOnly = false
                },
                onClosed = {
                    close()
                },
            )
        )
        val connection = try {
            connectionPool.acquire()
        } catch (ex: Exception) {
            try {
                LOGGER.warn("Failed to open database, delete existing database and retry", ex)
                databaseFile.deleteIfExists()
                connectionPool.acquire()
            } catch (ex: Exception) {
                throw RuntimeException("Failed to open model database", ex)
            }
        }
        try {
            runCatching {
                Files.setAttribute(databaseFile, "dos:hidden", true)
            }
            connection.createTables()
        } finally {
            connectionPool.release(connection)
        }
        this@ModelManager.connectionPool = connectionPool

        LOGGER.info("Initialized database")

        listenModelDir(ArmorStand.instance.scope)
        scheduleScan()
    }
}