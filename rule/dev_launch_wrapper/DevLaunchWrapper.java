package top.fifthlight.fabazel.devlaunchwrapper;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;

// Bazel don't allow us to transfer arguments in BUILD file, so let's hack
@AutoBazelRepository
public class DevLaunchWrapper {
    private static final String version = System.getProperty("dev.launch.version", null);
    private static final String type = System.getProperty("dev.launch.type", null);
    private static final String assetsPath = System.getProperty("dev.launch.assetsPath", null);
    private static final String mainClass = System.getProperty("dev.launch.mainClass", null);
    private static final String glfwLibName = System.getenv("GLFW_LIBNAME");
    private static final String copyFiles = System.getProperty("dev.launch.copyFiles", null);
    private static final String expandRunfileProperties = System.getProperty("dev.launch.expandRunfileProperties", null);

    private static class CopyDirectoryVisitor extends SimpleFileVisitor<Path> {
        private final Path fromPath;
        private final Path toPath;
        private final CopyOption[] copyOptions;

        public CopyDirectoryVisitor(Path fromPath, Path toPath, CopyOption... options) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOptions = options;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            var targetPath = toPath.resolve(fromPath.relativize(dir));
            Files.createDirectories(targetPath);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.copy(file, toPath.resolve(fromPath.relativize(file)), copyOptions);
            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException, IOException {
        var runfiles = Runfiles.preload().withSourceRepository(AutoBazelRepository_DevLaunchWrapper.NAME);

        if (expandRunfileProperties != null) {
            for (var property : expandRunfileProperties.split(",")) {
                var original = System.getProperty(property);
                System.setProperty(property, runfiles.rlocation(original));
            }
        }

        if (copyFiles != null) {
            var copyFileList = copyFiles.split(",");
            var workDir = Path.of(".").toRealPath();
            for (var entry : copyFileList) {
                var colonIndex = entry.indexOf(':');
                if (colonIndex == -1) {
                    throw new IllegalArgumentException("Invalid copy file entry: " + entry);
                }
                var fromStr = entry.substring(0, colonIndex);
                var from = Path.of(runfiles.rlocation(fromStr)).toRealPath();
                var to = workDir.resolve(entry.substring(colonIndex + 1));
                Files.createDirectories(to.getParent());
                if (Files.isDirectory(from)) {
                    Files.walkFileTree(from, new CopyDirectoryVisitor(from, to, StandardCopyOption.REPLACE_EXISTING));
                } else {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        var argsList = new ArrayList<>(Arrays.asList(args));

        if (glfwLibName != null) {
            System.setProperty("org.lwjgl.glfw.libname", glfwLibName);
        }

        if (assetsPath != null) {
            var realAssetsPath = runfiles.rlocation(Path.of(assetsPath).normalize().toString());
            var path = Path.of(realAssetsPath).toRealPath();
            argsList.add("--assetsDir");
            argsList.add(path.toString());
            if (version != null) {
                var versionPath = path.resolve(Path.of("versions", version));
                argsList.add("--assetIndex");
                argsList.add(Files.readString(versionPath));
            }
        }

        switch (type) {
            case "client" -> {
                var allowSymlinksPath = Path.of("allowed_symlinks.txt");
                Files.writeString(allowSymlinksPath, "[regex].*\n");
            }
            case "server" -> {
                var serverPropertiesPath = Path.of("server.properties");
                if (!Files.exists(serverPropertiesPath)) {
                    Files.writeString(serverPropertiesPath, "online-mode=false\n");
                }
                argsList.add("--nogui");
                var eulaPath = Path.of("eula.txt");
                Files.writeString(eulaPath, "eula=true\n");
            }
        }

        System.err.println("Launching game with arguments: " + String.join(" ", argsList));
        var array = new String[argsList.size()];
        array = argsList.toArray(array);

        if (mainClass == null) {
            throw new IllegalArgumentException("No main class specified. Specify your real main class with dev.launch.mainClass JVM property.");
        }
        var clazz = ClassLoader.getSystemClassLoader().loadClass(mainClass);
        var mainMethod = clazz.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) array);
    }
}