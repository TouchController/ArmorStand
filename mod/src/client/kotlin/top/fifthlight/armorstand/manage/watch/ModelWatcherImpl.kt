package top.fifthlight.armorstand.manage.watch

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate

class ModelWatcherImpl(
    private val rootDir: Path,
    private val filter: Predicate<Path>,
) : ModelWatcher {
    companion object {
        private val logger = LoggerFactory.getLogger(ModelWatcherImpl::class.java)
    }

    private val watchService = FileSystems.getDefault().newWatchService()
    private val watchKeys = ConcurrentHashMap<WatchKey, Path>()
    private val running = AtomicBoolean(false)

    @Volatile
    private var onChangedCallback: (Path) -> Unit = {}

    private var thread: Thread? = null

    override fun start(onChanged: (dir: Path) -> Unit) {
        onChangedCallback = onChanged
        if (!running.compareAndSet(false, true)) {
            return
        }

        registerAll(rootDir)

        thread = Thread {
            try {
                while (running.get()) {
                    val key = try {
                        watchService.take()
                    } catch (ex: InterruptedException) {
                        break
                    }

                    val dir = watchKeys[key] ?: continue
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue
                        }

                        @Suppress("UNCHECKED_CAST")
                        val name = (event.context() as Path)
                        val child = dir.resolve(name)

                        // Recursively register child directories
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                            registerAll(child)
                        }

                        when (kind) {
                            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY -> {
                                // Trigger callback
                                if (Files.isRegularFile(child) && filter.test(child)) {
                                    onChangedCallback(child)
                                }
                            }

                            else -> {}
                        }
                    }

                    // Reset or remove key
                    if (!key.reset()) {
                        watchKeys.remove(key)
                    }
                }
            } finally {
                // Do cleanup
                watchService.close()
            }
        }.apply {
            name = "ModelWatcher"
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        thread?.interrupt()
        // watchService will be cleaned in thread's finally block
        watchKeys.clear()
    }

    private fun registerAll(start: Path) {
        try {
            Files.walkFileTree(start, object : FileVisitor<Path> {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    try {
                        dir.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE
                        ).also { key ->
                            watchKeys[key] = dir
                        }
                    } catch (ex: IOException) {
                        logger.warn("Failed to register directory: {}", dir, ex)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult = FileVisitResult.CONTINUE

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException?,
                ) = FileVisitResult.CONTINUE

                override fun postVisitDirectory(
                    dir: Path,
                    exc: IOException?,
                ) = FileVisitResult.CONTINUE
            })
        } catch (ex: IOException) {
            logger.warn("Failed to register directory: {}", start, ex)
        }
    }
}
