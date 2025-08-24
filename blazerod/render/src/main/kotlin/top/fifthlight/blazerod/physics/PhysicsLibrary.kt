package top.fifthlight.blazerod.physics

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.BlazeRod
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrNull

object PhysicsLibrary {
    private val logger = LoggerFactory.getLogger(PhysicsLibrary::class.java)
    var isPhysicsAvailable = false
        private set

    private val androidPaths = listOf(
        Path.of("/", "system", "build.prop"),
        Path.of("/", "system", "bin", "app_process"),
        Path.of("/", "system", "framework", "framework.jar")
    )
    private val isAndroid: Boolean by lazy {
        androidPaths.any { path ->
            try {
                path.exists()
            } catch (ex: SecurityException) {
                logger.info("Failed to access $path, may running on Android", ex)
                true
            } catch (ex: IOException) {
                logger.info("Failed to access $path, may running on Android", ex)
                true
            }
        }
    }

    fun init() {
        if (isPhysicsAvailable) {
            return
        }

        logger.info("Loading libbulletjme natives...")

        val systemName = System.getProperty("os.name")
        val systemArch = System.getProperty("os.arch")
        logger.info("System name: $systemName, system arch: $systemArch")

        val (system, extension) = when {
            systemName.startsWith("Linux", ignoreCase = true) -> if (isAndroid) {
                "android" to "so"
            } else {
                "linux" to "so"
            }

            // Most OpenJDK on Android declare themselves as Linux, but just in case
            systemName.contains("Android", ignoreCase = true) -> "android" to "so"
            systemName.startsWith("Windows", ignoreCase = true) -> "windows" to "dll"
            else -> {
                logger.warn("Unknown system: $systemName, no physics available")
                return
            }
        }
        val fileName = "liblibbulletjme_natives.$extension"

        val arch = when (systemArch) {
            "x86_32", "x86", "i386", "i486", "i586", "i686" -> "x86_32"
            "amd64", "x86_64" -> "x86_64"
            "armeabi", "armeabi-v7a", "armhf", "arm", "armel" -> "armv7"
            "arm64", "aarch64" -> "aarch64"
            else -> null
        } ?: run {
            logger.warn("Unsupported arch: $systemArch, no physics available")
            return
        }
        val resourcePath = "libbulletjme_natives_${system}_${arch}/$fileName"

        val container = FabricLoader.getInstance().getModContainer("libbulletjme_natives_blazerod")
        val path = try {
            if (container.isEmpty) {
                if (!BlazeRod.debug) {
                    logger.warn("Failed to find natives JAR, no physics available")
                    return
                }
                val uri = javaClass.classLoader.getResource(resourcePath)?.toURI()
                if (uri?.scheme == "jar") {
                    runCatching {
                        FileSystems.newFileSystem(uri, mapOf("create" to "true"))
                    }
                }
                val path = uri?.toPath()
                if (path == null) {
                    logger.warn("Failed to find $resourcePath in resources, no physics available")
                    return
                }
                path
            } else {
                val file = container.get().findPath(resourcePath).getOrNull()
                if (file == null) {
                    logger.warn("Failed to find $resourcePath in natives JAR, no physics available")
                    return
                }
                file
            }
        } catch (ex: Exception) {
            logger.warn("Failed to get natives in JAR, no physics available", ex)
            return
        }

        val outputPath = Files.createTempFile("bulletjme", ".$extension")
        logger.info("Extracting $resourcePath to $outputPath")

        try {
            path.copyTo(outputPath, true)
            runCatching {
                // Set file to read only after extracting
                if (system == "windows") {
                    val attributeView = outputPath.fileAttributesView<DosFileAttributeView>()
                    attributeView.setReadOnly(true)
                } else {
                    val attributeView = outputPath.fileAttributesView<PosixFileAttributeView>()
                    // 500
                    attributeView.setPermissions(
                        setOf(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_EXECUTE
                        )
                    )
                }
            }
            System.load(outputPath.toAbsolutePath().toString())
            isPhysicsAvailable = true
            logger.info("Loaded libbulletjme natives")
        } catch (ex: Throwable) {
            logger.warn("Failed to load libbulletjme natives", ex)
        } finally {
            try {
                outputPath.deleteIfExists()
            } catch (ex: Exception) {
                outputPath.toFile().deleteOnExit()
                // Can't delete on Windows
            }
        }
    }
}