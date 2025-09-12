package top.fifthlight.blazerod.model.pmd

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.pmd.format.PmdBone
import top.fifthlight.blazerod.model.pmd.format.PmdHeader
import top.fifthlight.blazerod.model.pmd.format.PmdMaterial
import top.fifthlight.blazerod.model.util.MMD_SCALE
import top.fifthlight.blazerod.model.util.openChannelCaseInsensitive

import top.fifthlight.blazerod.model.util.readAll

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

class PmdLoadException(message: String) : Exception(message)

// Pmd loader
// Format from: https://mikumikudance.fandom.com/wiki/MMD:Polygon_Model_Data
class PmdLoader : ModelFileLoader {
    override val extensions = mapOf(
        "pmd" to setOf(ModelFileLoader.Ability.MODEL),
    )

    companion object {
        private val PMD_SIGNATURE = byteArrayOf(0x50, 0x6D, 0x64, 0x00, 0x00, 0x80u.toByte(), 0x3F)
        private val SHIFT_JIS = Charset.forName("Shift-JIS")
        private val decoder = SHIFT_JIS.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        //                                             POS NORM UV
        private const val BASE_VERTEX_ATTRIBUTE_SIZE = (3 + 3 + 2) * 4

        //                                           JOINT WEIGHT
        private const val SKIN_VERTEX_ATTRIBUTE_SIZE = (4 + 4) * 4
        private const val VERTEX_ATTRIBUTE_SIZE = BASE_VERTEX_ATTRIBUTE_SIZE + SKIN_VERTEX_ATTRIBUTE_SIZE
        private val logger = LoggerFactory.getLogger(PmdLoader::class.java)
    }

    override val probeLength = PMD_SIGNATURE.size
    override fun probe(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < PMD_SIGNATURE.size) return false
        val signatureBytes = ByteArray(PMD_SIGNATURE.size)
        buffer.get(signatureBytes, 0, PMD_SIGNATURE.size)
        return signatureBytes.contentEquals(PMD_SIGNATURE)
    }

    private class Context(
        val basePath: Path,
    ) {
        private lateinit var vertexBuffer: ByteBuffer
        private var vertices: Int = -1

        private lateinit var indexBuffer: ByteBuffer
        private var indices: Int = -1

        private lateinit var materials: List<PmdMaterial>
        private lateinit var bones: List<PmdBone>
        private val childBoneMap = mutableMapOf<Int, MutableList<Int>>()
        private val rootBones = mutableListOf<Int>()

        private fun loadString(buffer: ByteBuffer, maxLength: Int): String {
            val bytes = ByteBuffer.allocate(maxLength)
            bytes.put(buffer.slice(buffer.position(), maxLength))
            buffer.position(buffer.position() + maxLength)
            val nullIndex = (0 until maxLength).indexOfFirst { bytes.get(it) == 0.toByte() }
            val stringBytes = bytes.slice(0, nullIndex).order(ByteOrder.LITTLE_ENDIAN)
            return decoder.decode(stringBytes).toString()
        }

        private fun loadRgbColor(buffer: ByteBuffer): RgbColor {
            if (buffer.remaining() < 3 * 4) {
                throw PmdLoadException("Bad file: want to read Vec3 (12 bytes), but only have ${buffer.remaining()} bytes available")
            }
            return RgbColor(
                r = buffer.getFloat(),
                g = buffer.getFloat(),
                b = buffer.getFloat(),
            )
        }

        private fun loadRgbaColor(buffer: ByteBuffer): RgbaColor {
            if (buffer.remaining() < 4 * 4) {
                throw PmdLoadException("Bad file: want to read Vec4 (16 bytes), but only have ${buffer.remaining()} bytes available")
            }
            return RgbaColor(
                r = buffer.getFloat(),
                g = buffer.getFloat(),
                b = buffer.getFloat(),
                a = buffer.getFloat(),
            )
        }

        private fun loadVector3f(buffer: ByteBuffer): Vector3f {
            if (buffer.remaining() < 3 * 4) {
                throw PmdLoadException("Bad file: want to read Vec3 (12 bytes), but only have ${buffer.remaining()} bytes available")
            }
            return Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat())
        }

        private fun loadSignature(buffer: ByteBuffer) {
            if (buffer.remaining() < PMD_SIGNATURE.size) {
                throw PmdLoadException("Bad file: signature is ${PMD_SIGNATURE.size} bytes, but only ${buffer.remaining()} bytes in buffer")
            }
            if (PMD_SIGNATURE.any { buffer.get() != it }) {
                throw PmdLoadException("Bad PMX signature")
            }
        }

        private fun loadHeader(buffer: ByteBuffer): PmdHeader {
            loadSignature(buffer)
            return PmdHeader(
                name = loadString(buffer, 20),
                comment = loadString(buffer, 256),
            )
        }

        private fun loadVertices(buffer: ByteBuffer) {
            val vertexCount = buffer.getInt()
            if (vertexCount <= 0) {
                throw PmdLoadException("Bad vertices: count $vertexCount should be greater than zero")
            }

            val outputBuffer =
                ByteBuffer.allocateDirect(VERTEX_ATTRIBUTE_SIZE * vertexCount).order(ByteOrder.nativeOrder())
            var outputPosition = 0
            var inputPosition = buffer.position()

            fun readFloat(): Float = buffer.getFloat(inputPosition).also {
                inputPosition += 4
            }

            val copyBaseVertexSize = BASE_VERTEX_ATTRIBUTE_SIZE - 24
            // FORMAT: POSITION_NORMAL_UV_JOINT_WEIGHT
            for (i in 0 until vertexCount) {
                // Read vertex data, transform xyz
                val x = buffer.getFloat(inputPosition)
                val y = buffer.getFloat(inputPosition + 4)
                val z = buffer.getFloat(inputPosition + 8)
                outputBuffer.putFloat(outputPosition, x * -MMD_SCALE)
                outputBuffer.putFloat(outputPosition + 4, y * MMD_SCALE)
                outputBuffer.putFloat(outputPosition + 8, z * MMD_SCALE)
                outputPosition += 12
                inputPosition += 12

                // Read normal data, transform x
                val nx = buffer.getFloat(inputPosition)
                val ny = buffer.getFloat(inputPosition + 4)
                val nz = buffer.getFloat(inputPosition + 8)
                outputBuffer.putFloat(outputPosition, -nx)
                outputBuffer.putFloat(outputPosition + 4, ny)
                outputBuffer.putFloat(outputPosition + 8, nz)
                outputPosition += 12
                inputPosition += 12

                // POSITION_NORMAL_UV_JOINT_WEIGHT
                outputBuffer.put(outputPosition, buffer, inputPosition, copyBaseVertexSize)
                outputPosition += copyBaseVertexSize
                inputPosition += copyBaseVertexSize

                outputBuffer.putInt(outputPosition, buffer.getShort(inputPosition).toUShort().toInt())
                outputBuffer.putInt(outputPosition + 4, buffer.getShort(inputPosition + 2).toUShort().toInt())
                val weight = buffer.get(inputPosition + 4).toUByte().toFloat() / 100f
                outputBuffer.putFloat(outputPosition + 16, weight)
                outputBuffer.putFloat(outputPosition + 20, 1f - weight)
                outputPosition += SKIN_VERTEX_ATTRIBUTE_SIZE
                inputPosition += 6
            }
            require(outputPosition == outputBuffer.capacity()) { "Bug: Not filled the entire output buffer" }
            vertexBuffer = outputBuffer
            vertices = vertexCount
            buffer.position(inputPosition)
        }

        private fun loadIndices(buffer: ByteBuffer) {
            val indexCount = buffer.getInt()
            if (indexCount <= 0) {
                throw PmdLoadException("Bad indeices: count $indexCount should be greater than zero")
            }
            if (indexCount % 3 != 0) {
                throw PmdLoadException("Bad index count: $indexCount % 3 != 0")
            }

            val triangleCount = indexCount / 3
            val indexBufferSize = 2 * indexCount
            if (buffer.remaining() < indexBufferSize) {
                throw PmdLoadException("Bad index data: should have $indexBufferSize bytes, but only ${buffer.remaining()} bytes available")
            }

            val outputBuffer = ByteBuffer.allocateDirect(indexBufferSize).order(ByteOrder.nativeOrder())
            // PMD use clockwise indices, but OpenGL use counterclockwise indices, so let's invert the order here.
            for (i in 0 until triangleCount) {
                outputBuffer.putShort(buffer.getShort())
                val a = buffer.getShort()
                val b = buffer.getShort()
                outputBuffer.putShort(b)
                outputBuffer.putShort(a)
            }
            indexBuffer = outputBuffer
            indices = indexCount
        }

        private fun parseTextureInfo(info: String) = info.split('*').let {
            when (it.size) {
                1 -> Pair(it[0], null)
                2 -> Pair(it[0], it[1])
                else -> throw PmdLoadException("Bad texture info: $info")
            }
        }

        private fun loadMaterials(buffer: ByteBuffer) {
            val materialCount = buffer.getInt()
            materials = (0 until materialCount).map {
                val diffuseColor = loadRgbaColor(buffer)
                val specularStrength = buffer.getFloat()
                val specularColor = loadRgbColor(buffer)
                val ambientColor = loadRgbColor(buffer)
                val toonIndex = buffer.get().toInt()
                val edgeFlag = buffer.get() != 0.toByte()
                val verticesCount = buffer.getInt()
                val (texture, sphere) = parseTextureInfo(loadString(buffer, 20))
                PmdMaterial(
                    diffuseColor = diffuseColor,
                    specularStrength = specularStrength,
                    specularColor = specularColor,
                    ambientColor = ambientColor,
                    toonIndex = toonIndex,
                    edgeFlag = edgeFlag,
                    verticesCount = verticesCount,
                    textureFilename = texture.takeIf { it.isNotEmpty() },
                    sphereFilename = sphere,
                )
            }
        }

        private fun loadTexture(name: String): Texture? {
            try {
                val path = basePath.resolve(name)
                val buffer = path.openChannelCaseInsensitive(StandardOpenOption.READ).use { channel ->
                    val size = channel.size()
                    runCatching {
                        channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                    }.getOrNull() ?: run {
                        if (size > 256 * 1024 * 1024) {
                            throw PmdLoadException("Texture too large! Maximum supported is 256M.")
                        }
                        val size = size.toInt()
                        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                        channel.readAll(buffer)
                        buffer.flip()
                        buffer
                    }
                }
                return Texture(
                    name = name,
                    bufferView = BufferView(
                        buffer = Buffer(
                            name = "Texture $name",
                            buffer = buffer,
                        ),
                        byteLength = buffer.remaining(),
                        byteOffset = 0,
                        byteStride = 0,
                    ),
                    sampler = Texture.Sampler(),
                )
            } catch (ex: Exception) {
                logger.warn("Failed to load PMD texture", ex)
                return null
            }
        }

        private fun Vector3f.transformPosition() = also {
            mul(MMD_SCALE)
            x = -x
        }

        private fun loadBones(buffer: ByteBuffer) {
            val boneCount = buffer.getShort()
            if (boneCount < 0) {
                throw PmdLoadException("Bad PMD model: bones count less than zero")
            }

            fun loadBone(buffer: ByteBuffer): PmdBone = PmdBone(
                name = loadString(buffer, 20),
                parentBoneIndex = buffer.getShort().toInt().takeIf { it != -1 },
                tailBoneIndex = buffer.getShort().toInt().takeIf { it != -1 },
                type = buffer.get().toUByte().toInt(),
                targetBoneIndex = buffer.getShort().toInt().takeIf { it != -1 },
                position = loadVector3f(buffer).transformPosition(),
            )

            bones = (0 until boneCount).map { index ->
                loadBone(buffer).also { bone ->
                    bone.parentBoneIndex?.let { parentBoneIndex ->
                        childBoneMap.getOrPut(parentBoneIndex) { mutableListOf() }.add(index)
                    } ?: run {
                        rootBones.add(index)
                    }
                }
            }
        }

        fun load(buffer: ByteBuffer): ModelFileLoader.LoadResult {
            val header = loadHeader(buffer)
            loadVertices(buffer)
            loadIndices(buffer)
            loadMaterials(buffer)
            loadBones(buffer)

            val modelId = UUID.randomUUID()
            val rootNodes = mutableListOf<Node>()

            fun addBone(index: Int, parentPosition: Vector3f? = null): Node {
                val bone = bones[index]

                val children = childBoneMap[index]?.map { addBone(it, bone.position) } ?: listOf()

                return Node(
                    name = bone.name,
                    id = NodeId(modelId, index),
                    transform = NodeTransform.Decomposed(
                        translation = Vector3f().set(bone.position).also {
                            if (parentPosition != null) {
                                it.sub(parentPosition)
                            }
                        },
                        rotation = Quaternionf(),
                        scale = Vector3f(1f),
                    ),
                    children = children,
                    components = listOf(),
                )
            }

            rootBones.forEach { index ->
                rootNodes.add(addBone(index))
            }

            var nextNodeIndex = bones.size

            val skin = Skin(
                name = "PMD skin",
                joints = (0 until bones.size).map { NodeId(modelId, it) },
                inverseBindMatrices = bones.map { Matrix4f().translation(it.position).invertAffine() },
                jointHumanoidTags = bones.map { HumanoidTag.fromPmxJapanese(it.name) },
            )

            val vertexBuffer = Buffer(
                name = "PMD Vertex Buffer",
                buffer = vertexBuffer
            )
            val vertexBufferView = BufferView(
                buffer = vertexBuffer,
                byteLength = vertices * VERTEX_ATTRIBUTE_SIZE,
                byteOffset = 0,
                byteStride = VERTEX_ATTRIBUTE_SIZE,
            )
            val indexBuffer = Buffer(
                name = "PMD Index Buffer",
                buffer = indexBuffer,
            )
            val indexBufferView = BufferView(
                buffer = indexBuffer,
                byteLength = indices * 2,
                byteOffset = 0,
                byteStride = 0,
            )

            var indexOffset = 0
            materials.forEach { pmdMaterial ->
                val nodeIndex = nextNodeIndex++
                val nodeId = NodeId(modelId, nodeIndex)
                val meshId = MeshId(modelId, nodeIndex)
                val material = Material.Unlit(
                    name = null,
                    baseColor = pmdMaterial.diffuseColor,
                    baseColorTexture = pmdMaterial.textureFilename
                        ?.let(::loadTexture)
                        ?.let(Material::TextureInfo),
                    doubleSided = true,
                )

                Node(
                    id = nodeId,
                    components = buildList {
                        add(
                            NodeComponent.MeshComponent(
                                Mesh(
                                    id = meshId,
                                    primitives = listOf(
                                        Primitive(
                                            mode = Primitive.Mode.TRIANGLES,
                                            material = material,
                                            attributes = Primitive.Attributes.Primitive(
                                                position = Accessor(
                                                    bufferView = vertexBufferView,
                                                    byteOffset = 0,
                                                    componentType = Accessor.ComponentType.FLOAT,
                                                    normalized = false,
                                                    count = vertices,
                                                    type = Accessor.AccessorType.VEC3,
                                                ),
                                                normal = Accessor(
                                                    bufferView = vertexBufferView,
                                                    byteOffset = 3 * 4,
                                                    componentType = Accessor.ComponentType.FLOAT,
                                                    normalized = false,
                                                    count = vertices,
                                                    type = Accessor.AccessorType.VEC3,
                                                ),
                                                texcoords = listOf(
                                                    Accessor(
                                                        bufferView = vertexBufferView,
                                                        byteOffset = (3 + 3) * 4,
                                                        componentType = Accessor.ComponentType.FLOAT,
                                                        normalized = false,
                                                        count = vertices,
                                                        type = Accessor.AccessorType.VEC2,
                                                    )
                                                ),
                                                joints = listOf(
                                                    Accessor(
                                                        bufferView = vertexBufferView,
                                                        byteOffset = (3 + 3 + 2) * 4,
                                                        componentType = Accessor.ComponentType.UNSIGNED_INT,
                                                        normalized = false,
                                                        count = vertices,
                                                        type = Accessor.AccessorType.VEC4,
                                                    )
                                                ),
                                                weights = listOf(
                                                    Accessor(
                                                        bufferView = vertexBufferView,
                                                        byteOffset = (3 + 3 + 2 + 4) * 4,
                                                        componentType = Accessor.ComponentType.FLOAT,
                                                        normalized = false,
                                                        count = vertices,
                                                        type = Accessor.AccessorType.VEC4,
                                                    )
                                                )
                                            ),
                                            indices = Accessor(
                                                bufferView = indexBufferView,
                                                byteOffset = indexOffset * 2,
                                                componentType = Accessor.ComponentType.UNSIGNED_SHORT,
                                                normalized = false,
                                                count = pmdMaterial.verticesCount,
                                                type = Accessor.AccessorType.SCALAR,
                                            ),
                                            targets = listOf(),
                                        )
                                    ),
                                    weights = null,
                                )
                            )
                        )
                        add(
                            NodeComponent.SkinComponent(
                                skin = skin,
                                meshIds = listOf(meshId),
                            )
                        )
                    }
                ).also {
                    rootNodes.add(it)
                    indexOffset += pmdMaterial.verticesCount
                }
            }

            val scene = Scene(nodes = rootNodes)

            return ModelFileLoader.LoadResult(
                metadata = Metadata(
                    title = header.name,
                    comment = header.comment,
                ),
                model = Model(
                    scenes = listOf(scene),
                    skins = listOf(skin),
                    defaultScene = scene,
                ),
                animations = listOf(),
            )
        }
    }

    override fun load(path: Path, basePath: Path) =
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            val fileSize = channel.size()
            val buffer = runCatching {
                channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
            }.getOrNull() ?: run {
                if (fileSize > 32 * 1024 * 1024) {
                    throw PmdLoadException("PMD model size too large: maximum allowed is 32M, current is $fileSize")
                }
                val fileSize = fileSize.toInt()
                val buffer = ByteBuffer.allocate(fileSize)
                channel.readAll(buffer)
                buffer.flip()
                buffer
            }
            val context = Context(basePath)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            context.load(buffer)
        }
}
