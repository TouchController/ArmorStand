package top.fifthlight.blazerod.physics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PhysicsLibrary {
    private PhysicsLibrary() {
    }

    private static final Logger logger = LoggerFactory.getLogger(PhysicsLibrary.class);
    private static boolean isPhysicsAvailable = false;

    public native static long createPhysicsScene(ByteBuffer rigidBodies, ByteBuffer joints);

    public native static void destroyPhysicsScene(long physicsScene);

    public native static long createPhysicsWorld(long physicsScene, ByteBuffer initialTransform);

    public native static ByteBuffer getTransformBuffer(long physicsWorld);

    public native static void stepPhysicsWorld(long physicsWorld, float deltaTime, float maxSubSteps, float fixedTimeStep);

    public native static void destroyPhysicsWorld(long physicsWorld);

    public static boolean isPhysicsAvailable() {
        return isPhysicsAvailable;
    }

    private static final List<Path> androidPaths = List.of(
            Path.of("/", "system", "build.prop"),
            Path.of("/", "system", "bin", "app_process"),
            Path.of("/", "system", "framework", "framework.jar")
    );

    public static boolean load() {
        if (isPhysicsAvailable) {
            return true;
        }

        logger.info("Loading bullet physics native library");

        var systemName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        var systemArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        String system;
        String extension;
        if (systemName.startsWith("linux")) {
            var isAndroid = false;
            for (var path : androidPaths) {
                try {
                    if (Files.exists(path)) {
                        isAndroid = true;
                        break;
                    }
                } catch (SecurityException ex) {
                    logger.info("Failed to access {}, may running on Android", path, ex);
                }
            }

            if (isAndroid) {
                system = "android";
            } else {
                system = "linux";
            }
            extension = "so";
        } else if (systemName.startsWith("windows")) {
            system = "windows";
            extension = "dll";
        } else if (systemName.contains("android")) {
            // Most OpenJDK on Android declare themselves as Linux, but just in case
            system = "android";
            extension = "so";
        } else {
            logger.error("Unsupported system: {}", systemName);
            return false;
        }

        var arch = switch (systemArch) {
            case "amd64", "x86_64" -> "x86_64";
            case "arm64", "aarch64" -> "aarch64";
            default -> null;
        };
        if (arch == null) {
            logger.error("Unsupported architecture: {}", systemArch);
            return false;
        }

        var resourcePath = "bullet_%s_%s/libbullet.%s".formatted(system, arch, extension);
        try (var libraryUrl = PhysicsLibrary.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (libraryUrl == null) {
                logger.error("Failed to find physics library: {}", resourcePath);
                return false;
            }

            var outputPath = Files.createTempFile("bullet_", "." + extension);
            logger.info("Extracting {} to {}", resourcePath, outputPath);

            Files.copy(libraryUrl, outputPath, StandardCopyOption.REPLACE_EXISTING);
            try {
                // Set file to read only after extracting
                if ("windows".equals(system)) {
                    var attributeView = Files.getFileAttributeView(outputPath, DosFileAttributeView.class);
                    attributeView.setReadOnly(true);
                } else {
                    var attributeView = Files.getFileAttributeView(outputPath, PosixFileAttributeView.class);
                    // 500
                    attributeView.setPermissions(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                }
            } catch (Exception ignored) {
            }

            System.load(outputPath.toAbsolutePath().toString());
            try {
                Files.delete(outputPath);
            } catch (Exception ignored) {
                // On Windows, the file is locked.
            }

            isPhysicsAvailable = true;
            logger.info("Loaded bullet physics native library");
            return true;
        } catch (Exception ex) {
            logger.error("Failed to load bullet physics native library", ex);
            return false;
        }
    }
}
