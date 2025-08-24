package top.fifthlight.fabazel.remapper

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern
import kotlin.io.path.Path

object TinyRemapperWorker {
    private val format = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val mappings = MappingManager()
    private val MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+")

    @JvmStatic
    fun main(args: Array<String>) {
        while (true) {
            val line = readlnOrNull() ?: break
            System.err.println(line)
            if (line.isEmpty()) {
                continue
            }
            val workRequest: WorkRequest = format.decodeFromString(line)
            val workResponse = handleRequest(workRequest)
            println(format.encodeToString(workResponse))
            System.gc()
        }

        mappings.close()
    }

    private fun handleRequest(request: WorkRequest): WorkResponse {
        val inputs = request.inputs.associateBy { it.path }
        fun getInputFileHash(file: String): String = inputs[file]?.digest ?: error("Bad input file: $file")
        val logBuffer = StringWriter()
        val logWriter = PrintWriter(logBuffer)
        try {
            val (parameters, arguments) = request.arguments.partition { it.startsWith("--") }

            var mixin = false
            var fixPackageAccess = false
            var remapAccessWidener = false
            var removeJarInJar = false
            for (parameter in parameters) {
                val name = parameter.removePrefix("--")
                when (name) {
                    "mixin" -> mixin = true
                    "fix_package_access" -> fixPackageAccess = true
                    "remap_access_widener" -> remapAccessWidener = true
                    "remove_jar_in_jar" -> removeJarInJar = true
                }
            }

            if (arguments.size < 5) {
                return WorkResponse(
                    requestId = request.requestId,
                    exitCode = 1,
                    output = "Bad count of arguments: ${arguments.size}, at least 5",
                )
            }

            val (inputJar, outputJar, mappingPath, fromNamespace, toNamespace) = arguments
            val classpath = arguments.subList(5, arguments.size).map(::Path)

            val mappingArgument = MappingManager.Argument(
                mapping = Path(mappingPath),
                mappingHash = getInputFileHash(mappingPath),
                fromNamespace = fromNamespace,
                toNamespace = toNamespace,
            )
            val entry = mappings[mappingArgument]

            val logger = PrintLogger(logWriter)

            val remapper = TinyRemapper
                .newRemapper(logger)
                .apply {
                    withMappings(entry.provider)
                    if (mixin) {
                        extension(MixinExtension())
                    }
                    renameInvalidLocals(true)
                    rebuildSourceFilenames(true)
                    invalidLvNamePattern(MC_LV_PATTERN)
                    resolveMissing(true)
                    inferNameFromSameLvIndex(true)
                    if (fixPackageAccess) {
                        fixPackageAccess(true)
                        checkPackageAccess(true)
                    }
                }
                .build()

            val input = Path(inputJar)
            try {
                OutputConsumerPath.Builder(Path(outputJar))
                    .assumeArchive(true)
                    .build()
                    .use { output ->
                        val nonClassFilesProcessors = buildList {
                            if (removeJarInJar) {
                                add(JarInJarRemover)
                            }
                            addAll(NonClassCopyMode.FIX_META_INF.remappers)
                            if (remapAccessWidener) {
                                add(
                                    AccessWidenerRemapper(
                                        entry.remapper,
                                        fromNamespace,
                                        toNamespace
                                    )
                                )
                            }
                        }
                        output.addNonClassFiles(input, remapper, nonClassFilesProcessors)
                        remapper.readInputs(input)
                        classpath.forEach { remapper.readClassPath(it) }
                        remapper.apply(output)
                    }
            } finally {
                remapper.finish()
            }
            return WorkResponse(
                requestId = request.requestId,
                output = logBuffer.toString(),
                exitCode = 0,
            )
        } catch (ex: Exception) {
            ex.printStackTrace(logWriter)
            return WorkResponse(
                requestId = request.requestId,
                exitCode = 1,
                output = logBuffer.toString(),
            )
        }
    }
}
