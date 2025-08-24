package top.fifthlight.fabazel.remapper

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.outputStream

object JarInJarRemover : OutputConsumerPath.ResourceRemapper {
    private fun Path.isJarInJar() = nameCount == 3
            && getName(0).name == "META-INF"
            && getName(1).name == "jars"
            && fileName.extension.equals("jar", true)

    private fun Path.isFabricModJson() = nameCount == 1 && fileName.toString().equals("fabric.mod.json", true)

    override fun canTransform(remapper: TinyRemapper, path: Path): Boolean = path.isJarInJar() || path.isFabricModJson()

    override fun transform(
        destinationDirectory: Path,
        relativePath: Path,
        input: InputStream,
        tinyRemapper: TinyRemapper,
    ) {
        if (relativePath.isJarInJar()) {
            return
        }
        if (relativePath.isFabricModJson()) {
            val outputFile = destinationDirectory.resolve(relativePath.toString())
            val textContent = input.reader().use { it.readText() }
            val jsonContent = Json.parseToJsonElement(textContent)
            val newContent = if (jsonContent is JsonObject) {
                JsonObject(jsonContent.filterKeys { it != "jars" })
            } else {
                jsonContent
            }
            outputFile.outputStream().writer().use { output ->
                output.write(Json.encodeToString(newContent))
            }
        }
    }
}