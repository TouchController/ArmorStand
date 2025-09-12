package top.fifthlight.blazerod.model.bedrock

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.joml.*
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.bedrock.metadata.ModelMetadata
import top.fifthlight.blazerod.model.util.readToBuffer
import top.fifthlight.blazerod.model.util.toRadian
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.bufferedReader
import kotlin.math.floor

internal class BedrockModelJsonLoader(
    private val basePath: Path,
    private val file: ModelMetadata.Files.File,
) {
    private lateinit var materials: List<Material>
    private fun loadTextures() {
        materials = file.texture.map { material ->
            val baseColorPathStr = when (material) {
                is ModelMetadata.Files.Texture.Path -> material.path
                is ModelMetadata.Files.Texture.Pbr -> material.uv
            }
            val baseColorPath = basePath.resolve(baseColorPathStr)
            val baseColorTexture = FileChannel.open(baseColorPath, StandardOpenOption.READ).use { channel ->
                val buffer = channel.readToBuffer(
                    readSizeLimit = 16 * 1024 * 1024,
                )
                Texture(
                    name = baseColorPathStr,
                    bufferView = BufferView(
                        buffer = Buffer(buffer = buffer),
                        byteOffset = 0,
                        byteLength = buffer.remaining(),
                        byteStride = 0,
                    ),
                    type = null,
                    sampler = Texture.Sampler(
                        magFilter = Texture.Sampler.MagFilter.NEAREST,
                        minFilter = Texture.Sampler.MinFilter.NEAREST,
                    ),
                )
            }
            Material.Vanilla(
                name = baseColorPathStr,
                baseColorTexture = Material.TextureInfo(baseColorTexture),
            )
        }
    }

    private data class ParseContext(
        val formatVersion: String = "",
        val geometries: List<GeometryContext>,
        val nameGeometryMap: Map<String, GeometryContext>,
    ) {
        data class GeometryContext(
            val identifier: String,
            val textureWidth: Int,
            val textureHeight: Int,
            val cubeCount: Int,
            val bones: List<BoneContext>,
            val nameBoneMap: Map<String, BoneContext>,
        ) {
            lateinit var rootBoneNodes: List<BoneNode>
            lateinit var nameBoneNodeMap: Map<String, BoneNode>
        }

        data class BoneNode(
            val bone: BoneContext,
            val transformMatrix: Matrix4fc,
            val children: MutableList<BoneNode>,
        )

        data class BoneContext(
            val name: String,
            val parent: String?,
            val pivot: Vector3fc,
            val rotation: Vector3fc,
            val mirror: Boolean,
            val inflate: Float,
        ) {
            var boneNode: BoneNode? = null
        }
    }

    private lateinit var parseContext: ParseContext
    private fun preprocess(path: Path) = path
        .bufferedReader()
        .let(::JsonReader)
        .with {
            var formatVersion: String? = null
            val geometries = mutableListOf<ParseContext.GeometryContext>()
            obj { key ->
                when (key) {
                    "format_version" -> formatVersion = nextString()
                    "minecraft:geometry" -> {
                        array {
                            var identifier: String? = null
                            var textureWidth = 64
                            var textureHeight = 64
                            var cubeCount = 0
                            val bones = mutableListOf<ParseContext.BoneContext>()
                            obj { key ->
                                when (key) {
                                    "description" -> obj { key ->
                                        when (key) {
                                            "identifier" -> identifier = nextString()
                                            "texture_width" -> textureWidth = nextInt()
                                            "texture_height" -> textureHeight = nextInt()
                                            else -> skipValue()
                                        }
                                    }

                                    "bones" -> {
                                        val boneNames = mutableSetOf<String>()
                                        array {
                                            var boneName: String? = null
                                            var parentName: String? = null
                                            val bonePivot = Vector3f()
                                            val boneRotation = Vector3f()
                                            var boneMirror = false
                                            var boneInflate = 0f
                                            obj { key ->
                                                when (key) {
                                                    "name" -> boneName = nextString()
                                                    "parent" -> parentName = nextString()
                                                    "pivot" -> vec3(bonePivot)
                                                    "rotation" -> vec3(boneRotation)
                                                    "mirror" -> boneMirror = nextBoolean()
                                                    "inflate" -> boneInflate = nextDouble().toFloat()
                                                    "cubes" -> array {
                                                        cubeCount++
                                                        skipValue()
                                                    }

                                                    else -> skipValue()
                                                }
                                            }
                                            if (boneName in boneNames) {
                                                throw BedrockModelLoadException("Duplicate bone name: $boneName")
                                            }
                                            boneName ?: throw BedrockModelLoadException("No bone name")
                                            boneNames += boneName
                                            bones += ParseContext.BoneContext(
                                                name = boneName,
                                                parent = parentName,
                                                pivot = bonePivot,
                                                rotation = boneRotation,
                                                mirror = boneMirror,
                                                inflate = boneInflate,
                                            )
                                        }
                                    }

                                    else -> skipValue()
                                }
                            }
                            geometries += ParseContext.GeometryContext(
                                identifier = identifier ?: throw BedrockModelLoadException("No identifier"),
                                textureWidth = textureWidth,
                                textureHeight = textureHeight,
                                cubeCount = cubeCount,
                                bones = bones,
                                nameBoneMap = bones.associateBy { it.name },
                            )
                        }
                    }

                    else -> skipValue()
                }
            }
            parseContext = ParseContext(
                formatVersion = formatVersion ?: throw BedrockModelLoadException("No format version"),
                geometries = geometries,
                nameGeometryMap = geometries.associateBy { it.identifier },
            )
        }

    private fun populateStructure() {
        val bonePivot = Vector3f()
        val bonePivotNeg = Vector3f()
        for (geometry in parseContext.geometries) {
            val rootBoneNodes = mutableListOf<ParseContext.BoneNode>()
            val nameBoneNodeMap = mutableMapOf<String, ParseContext.BoneNode>()
            for (bone in geometry.bones) {
                fun makeBoneNode(bone: ParseContext.BoneContext): ParseContext.BoneNode {
                    bone.boneNode?.let { return it }
                    val parent = bone.parent?.let { geometry.nameBoneMap[it] }
                    return if (parent == null) {
                        bone.pivot.div(16f, bonePivot)
                        bonePivot.negate(bonePivotNeg)
                        val boneRotation = bone.rotation
                        val boneTransformMatrix = Matrix4f()
                            .translation(bonePivot)
                            .rotateXYZ(
                                -boneRotation.x().toRadian(),
                                -boneRotation.y().toRadian(),
                                -boneRotation.z().toRadian(),
                            )
                            .translate(bonePivotNeg)
                        ParseContext.BoneNode(
                            bone = bone,
                            transformMatrix = boneTransformMatrix,
                            children = mutableListOf(),
                        ).also {
                            nameBoneNodeMap[bone.name] = it
                            rootBoneNodes += it
                        }
                    } else {
                        val parentNode = makeBoneNode(parent)

                        bone.pivot.div(16f, bonePivot)
                        bonePivot.negate(bonePivotNeg)
                        val boneRotation = bone.rotation
                        val boneTransformMatrix = Matrix4f()
                            .set(parentNode.transformMatrix)
                            .translate(bonePivot)
                            .rotateXYZ(
                                -boneRotation.x().toRadian(),
                                -boneRotation.y().toRadian(),
                                -boneRotation.z().toRadian(),
                            )
                            .translate(bonePivotNeg)

                        ParseContext.BoneNode(
                            bone = bone,
                            transformMatrix = boneTransformMatrix,
                            children = mutableListOf(),
                        ).also {
                            nameBoneNodeMap[bone.name] = it
                            parentNode.children += it
                        }
                    }
                }
                makeBoneNode(bone)
            }
            geometry.rootBoneNodes = rootBoneNodes
            geometry.nameBoneNodeMap = nameBoneNodeMap
        }
    }

    private class MeshBuilder(cubeCount: Int) {
        companion object {
            // POSITION_NORMAL_UV_JOINT_WEIGHT
            //                     POS NORM UV JOINT WEIGHT
            const val VERTEX_SIZE = 12 + 12 + 8 + 8 + 16
        }

        private val vertexBuffer = ByteBuffer
            .allocateDirect(cubeCount * 24 * VERTEX_SIZE)
            .order(ByteOrder.nativeOrder())

        // joint only have one element, and weight must be [1f, 0f, 0f, 0f]
        fun vertex(position: Vector3fc, normal: Vector3fc, uv: Vector2fc, joint: UShort) {
            vertexBuffer.putFloat(position.x())
            vertexBuffer.putFloat(position.y())
            vertexBuffer.putFloat(position.z())
            vertexBuffer.putFloat(normal.x())
            vertexBuffer.putFloat(normal.y())
            vertexBuffer.putFloat(normal.z())
            vertexBuffer.putFloat(uv.x())
            vertexBuffer.putFloat(uv.y())
            vertexBuffer.putShort(joint.toShort())
            vertexBuffer.putShort(0)
            vertexBuffer.putShort(0)
            vertexBuffer.putShort(0)
            vertexBuffer.putFloat(1f)
            vertexBuffer.putFloat(0f)
            vertexBuffer.putFloat(0f)
            vertexBuffer.putFloat(0f)
        }

        private var finished = false
        fun finish(): ByteBuffer {
            check(!finished) { "Already finished" }
            finished = true
            return vertexBuffer.position(0)
        }
    }

    private lateinit var vertexBuffers: List<ByteBuffer>

    private data class FaceUvInfo(
        val uv: Vector2f = Vector2f(),
        val uvSize: Vector2f = Vector2f(),
        var uvRotation: Int = 0,
    ) {
        fun reset() {
            uv.set(0f)
            uvRotation = 0
        }

        fun setFaceSize(faceWidth: Float, faceHeight: Float) {
            uvSize.set(faceWidth, faceHeight)
        }
    }

    companion object {
        // Six faces: corner indices + normals
        private val faceCornerIndicesList = listOf(
            intArrayOf(0, 2, 3, 1),  // north (-Z)
            intArrayOf(4, 5, 7, 6),  // south (+Z)
            intArrayOf(0, 4, 6, 2),  // west  (-X)
            intArrayOf(1, 3, 7, 5),  // east  (+X)
            intArrayOf(2, 6, 7, 3),  // up    (+Y)
            intArrayOf(0, 1, 5, 4),  // down  (-Y)
        )
        private val faceCornerUvList = listOf(
            intArrayOf(0, 2, 3, 1),  // north (-Z)
            intArrayOf(1, 0, 2, 3),  // south (+Z)
            intArrayOf(1, 0, 2, 3),  // west  (-X)
            intArrayOf(0, 2, 3, 1),  // east  (+X)
            intArrayOf(3, 1, 0, 2),  // up    (+Y)
            intArrayOf(0, 1, 3, 2),  // down  (-Y)
        )
        private val faceNormalsList = listOf(
            Vector3f(0f, 0f, -1f),  // north (-Z)
            Vector3f(0f, 0f, 1f),   // south (+Z)
            Vector3f(-1f, 0f, 0f),  // west  (-X)
            Vector3f(1f, 0f, 0f),   // east  (+X)
            Vector3f(0f, 1f, 0f),   // up    (+Y)
            Vector3f(0f, -1f, 0f),  // down  (-Y)
        )
        private val triangleOrder = intArrayOf(0, 1, 2, 0, 2, 3)
        private val cubeIndices = ShortArray(6 * 6).apply {
            var p = 0
            for (face in 0 until 6) {
                val base = face * 4
                for (t in triangleOrder) {
                    this[p++] = (base + t).toShort()
                }
            }
        }
    }

    private fun loadGeometries(path: Path) = path
        .bufferedReader()
        .let(::JsonReader)
        .with {
            val vertexBufferList = mutableListOf<ByteBuffer>()
            var geometryArrayIndex = 0

            val cubeOrigin = Vector3f()
            val cubeSize = Vector3f()
            val cubePivot = Vector3f()
            val cubePivotNeg = Vector3f()
            val cubeRotation = Vector3f()
            var cubeInflate: Float
            var cubeMirror: Boolean

            val northFaceUvInfo = FaceUvInfo()
            val southFaceUvInfo = FaceUvInfo()
            val eastFaceUvInfo = FaceUvInfo()
            val westFaceUvInfo = FaceUvInfo()
            val upFaceUvInfo = FaceUvInfo()
            val downFaceUvInfo = FaceUvInfo()
            val boxUv = Vector2f()

            val faceUvInfoList = listOf(
                northFaceUvInfo,
                southFaceUvInfo,
                westFaceUvInfo,
                eastFaceUvInfo,
                upFaceUvInfo,
                downFaceUvInfo,
            )

            val localCornerPoints = Array(8) { Vector3f() }
            val transformedCornerPoints = Array(8) { Vector3f() }
            val originalUvsForFace = Array(4) { Vector2f() }
            val rotatedUvsForFace = Array(4) { Vector2f() }
            val transformedNormal = Vector3f()

            val cubeTransformMatrix = Matrix4f()

            obj { topLevelKey ->
                when (topLevelKey) {
                    "minecraft:geometry" -> {
                        array {
                            val geometryContext = parseContext.geometries[geometryArrayIndex++]
                            val meshBuilder = MeshBuilder(geometryContext.cubeCount)

                            obj { key ->
                                when (key) {
                                    "bones" -> {
                                        var boneIndex = 0
                                        array {
                                            val boneIndex = boneIndex++
                                            val boneInfo = geometryContext.bones[boneIndex]
                                            val boneNode = geometryContext.nameBoneNodeMap[boneInfo.name]!!
                                            val boneTransformMatrix = boneNode.transformMatrix
                                            val jointId = boneIndex.toUShort()

                                            obj { key ->
                                                when (key) {
                                                    "cubes" -> array {
                                                        cubeOrigin.set(0f, 0f, 0f)
                                                        cubeSize.set(0f, 0f, 0f)
                                                        cubePivot.set(0f, 0f, 0f)
                                                        cubeRotation.set(0f, 0f, 0f)
                                                        cubeInflate = boneInfo.inflate
                                                        cubeMirror = boneInfo.mirror

                                                        var isBoxUv = false
                                                        northFaceUvInfo.reset()
                                                        southFaceUvInfo.reset()
                                                        eastFaceUvInfo.reset()
                                                        westFaceUvInfo.reset()
                                                        upFaceUvInfo.reset()
                                                        downFaceUvInfo.reset()

                                                        obj { bonePropertyKey ->
                                                            when (bonePropertyKey) {
                                                                "origin" -> vec3(cubeOrigin)
                                                                "size" -> vec3(cubeSize)
                                                                "pivot" -> vec3(cubePivot)
                                                                "rotation" -> vec3(cubeRotation)
                                                                "inflate" -> cubeInflate = nextDouble().toFloat()
                                                                "mirror" -> cubeMirror = nextBoolean()
                                                                "uv" -> {
                                                                    when (peek()) {
                                                                        JsonToken.BEGIN_ARRAY -> {
                                                                            isBoxUv = true
                                                                            beginArray()
                                                                            boxUv.set(
                                                                                nextDouble().toFloat(),
                                                                                nextDouble().toFloat(),
                                                                            )
                                                                            endArray()
                                                                        }

                                                                        JsonToken.BEGIN_OBJECT -> obj { faceName ->
                                                                            val targetFaceUv = when (faceName) {
                                                                                "north" -> northFaceUvInfo
                                                                                "south" -> southFaceUvInfo
                                                                                "east" -> eastFaceUvInfo
                                                                                "west" -> westFaceUvInfo
                                                                                "up" -> upFaceUvInfo
                                                                                "down" -> downFaceUvInfo
                                                                                else -> throw BedrockModelLoadException(
                                                                                    "Invalid UV face: $faceName"
                                                                                )
                                                                            }
                                                                            obj { uvProperty ->
                                                                                when (uvProperty) {
                                                                                    "uv" -> vec2(targetFaceUv.uv)
                                                                                    "uv_size" -> vec2(targetFaceUv.uvSize)
                                                                                    "uv_rotation" -> targetFaceUv.uvRotation =
                                                                                        nextInt()

                                                                                    else -> skipValue()
                                                                                }
                                                                            }
                                                                        }

                                                                        else -> throw BedrockModelLoadException("Invalid UV")
                                                                    }
                                                                }

                                                                else -> skipValue()
                                                            }
                                                        }

                                                        // Flatten box UVs to face UVs
                                                        if (isBoxUv) {
                                                            val u = boxUv.x
                                                            val v = boxUv.y
                                                            val dx = floor(cubeSize.x)
                                                            val dy = floor(cubeSize.y)
                                                            val dz = floor(cubeSize.z)

                                                            // North Face (-Z)
                                                            northFaceUvInfo.uv.set(u + dz, v + dz)
                                                            northFaceUvInfo.setFaceSize(dx, dy)

                                                            // South Face (+Z)
                                                            southFaceUvInfo.uv.set(u + dz + dx + dz, v + dz)
                                                            southFaceUvInfo.setFaceSize(dx, dy)

                                                            // West Face (-X)
                                                            if (cubeMirror) {
                                                                // Mirrored: West UV is on the East side
                                                                westFaceUvInfo.uv.set(u, v + dz)
                                                                // Flip UV by using a negative width
                                                                westFaceUvInfo.setFaceSize(-dz, dy)
                                                            } else {
                                                                westFaceUvInfo.uv.set(u + dz + dx, v + dz)
                                                                westFaceUvInfo.setFaceSize(dz, dy)
                                                            }

                                                            // East Face (+X)
                                                            if (cubeMirror) {
                                                                // Mirrored: East UV is on the West side
                                                                eastFaceUvInfo.uv.set(u + dz + dx, v + dz)
                                                                // Flip UV by using a negative width
                                                                eastFaceUvInfo.setFaceSize(-dz, dy)
                                                            } else {
                                                                eastFaceUvInfo.uv.set(u, v + dz)
                                                                eastFaceUvInfo.setFaceSize(dz, dy)
                                                            }

                                                            // Up Face (+Y)
                                                            upFaceUvInfo.uv.set(u + dz, v)
                                                            upFaceUvInfo.setFaceSize(dx, dz)

                                                            // Down Face (-Y)
                                                            downFaceUvInfo.uv.set(u + dz + dx, v)
                                                            downFaceUvInfo.setFaceSize(dx, dz)
                                                        }

                                                        // Convert pixel coordinates to block coordinates
                                                        cubeSize.div(16f)
                                                        cubeOrigin.div(16f)
                                                        cubePivot.div(16f)
                                                        cubePivot.negate(cubePivotNeg)
                                                        cubeInflate /= 16f

                                                        // Build local cube corners
                                                        for (cornerIndex in 0 until 8) {
                                                            localCornerPoints[cornerIndex].set(
                                                                if ((cornerIndex and 1) != 0) {
                                                                    cubeSize.x + cubeInflate
                                                                } else {
                                                                    -cubeInflate
                                                                },
                                                                if ((cornerIndex and 2) != 0) {
                                                                    cubeSize.y + cubeInflate
                                                                } else {
                                                                    -cubeInflate
                                                                },
                                                                if ((cornerIndex and 4) != 0) {
                                                                    cubeSize.z + cubeInflate
                                                                } else {
                                                                    -cubeInflate
                                                                }
                                                            )
                                                        }

                                                        // Bake pivot+rotation into transformMatrix
                                                        cubeTransformMatrix
                                                            .set(boneTransformMatrix)
                                                            .translate(cubePivot)
                                                            .rotateXYZ(
                                                                -cubeRotation.x.toRadian(),
                                                                -cubeRotation.y.toRadian(),
                                                                -cubeRotation.z.toRadian(),
                                                            )
                                                            .translate(cubePivotNeg)
                                                            .translate(cubeOrigin)

                                                        // Apply transform to each corner
                                                        for (cornerIndex in 0 until 8) {
                                                            cubeTransformMatrix.transformPosition(
                                                                localCornerPoints[cornerIndex],
                                                                transformedCornerPoints[cornerIndex]
                                                            )
                                                        }

                                                        val textureWidth = geometryContext.textureWidth.toFloat()
                                                        val textureHeight = geometryContext.textureHeight.toFloat()

                                                        // Emit six faces
                                                        for (faceIndex in 0 until 6) {
                                                            val cornerIndicesForThisFace =
                                                                faceCornerIndicesList[faceIndex]
                                                            val cornerUvsForThisFace =
                                                                faceCornerUvList[faceIndex]
                                                            val normalForThisFace = faceNormalsList[faceIndex]
                                                            val uvInfoForThisFace = faceUvInfoList[faceIndex]

                                                            // Transform the normal using the matrix
                                                            cubeTransformMatrix.transformDirection(
                                                                normalForThisFace,
                                                                transformedNormal,
                                                            ).normalize()

                                                            // Calculate the UV
                                                            val uBase = uvInfoForThisFace.uv.x / textureWidth
                                                            val vBase = uvInfoForThisFace.uv.y / textureHeight
                                                            val uSize = uvInfoForThisFace.uvSize.x / textureWidth
                                                            val vSize = uvInfoForThisFace.uvSize.y / textureHeight

                                                            originalUvsForFace[0].set(uBase + uSize, vBase + vSize)
                                                            originalUvsForFace[1].set(uBase, vBase + vSize)
                                                            originalUvsForFace[2].set(uBase + uSize, vBase)
                                                            originalUvsForFace[3].set(uBase, vBase)

                                                            val rotationSteps =
                                                                (uvInfoForThisFace.uvRotation / 90 % 4 + 4) % 4
                                                            for (cornerStep in 0 until 4) {
                                                                rotatedUvsForFace[cornerStep]
                                                                    .set(originalUvsForFace[(cornerStep + rotationSteps) % 4])
                                                            }

                                                            for (cornerStep in 0 until 4) {
                                                                val cornerVertexIndex =
                                                                    cornerIndicesForThisFace[cornerStep]
                                                                val cornerUvIndex =
                                                                    cornerUvsForThisFace[cornerStep]
                                                                meshBuilder.vertex(
                                                                    transformedCornerPoints[cornerVertexIndex],
                                                                    transformedNormal,
                                                                    rotatedUvsForFace[cornerUvIndex],
                                                                    jointId
                                                                )
                                                            }
                                                        }
                                                    }

                                                    else -> skipValue()
                                                }
                                            }
                                        }
                                    }

                                    else -> skipValue()
                                }
                            }
                            vertexBufferList += meshBuilder.finish()
                        }
                    }

                    else -> skipValue()
                }
            }
            this@BedrockModelJsonLoader.vertexBuffers = vertexBufferList
        }

    private lateinit var indexBuffer: ByteBuffer

    // Build a shared index buffer
    private fun buildIndexBuffer() {
        val totalCubes = parseContext.geometries.maxOf { it.cubeCount }
        val totalIndices = totalCubes * cubeIndices.size
        val indexBuffer = ByteBuffer
            .allocateDirect(totalIndices * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        var vertexOffset = 0
        repeat(totalCubes) {
            for (idx in cubeIndices) {
                indexBuffer.putShort((vertexOffset + idx).toShort())
            }
            vertexOffset += 24
        }
        indexBuffer.flip()
        this.indexBuffer = indexBuffer
    }

    private lateinit var scenes: List<Scene>
    private lateinit var skins: List<Skin>
    private fun assembleScenes() {
        val modelUuid = UUID.randomUUID()
        var nodeIndex = 0
        var meshIndex = 0
        val indexBufferView = BufferView(
            buffer = Buffer(
                buffer = indexBuffer,
            ),
            byteOffset = 0,
            byteLength = indexBuffer.remaining(),
            byteStride = 0,
        )
        scenes = parseContext.geometries.mapIndexed { geometryIndex, geometry ->
            val vertexBuffer = vertexBuffers[geometryIndex]
            val vertexBufferView = BufferView(
                buffer = Buffer(buffer = vertexBuffer),
                byteOffset = 0,
                byteLength = vertexBuffer.remaining(),
                byteStride = MeshBuilder.VERTEX_SIZE,
            )
            val meshNode = Node(
                name = "Meshes",
                id = NodeId(modelUuid, nodeIndex++),
                components = listOf(
                    NodeComponent.MeshComponent(
                        mesh = Mesh(
                            id = MeshId(modelUuid, meshIndex++),
                            primitives = listOf(
                                Primitive(
                                    mode = Primitive.Mode.TRIANGLES,
                                    material = materials.firstOrNull() ?: Material.Unlit(name = "default"),
                                    attributes = Primitive.Attributes.Primitive(
                                        position = Accessor(
                                            bufferView = vertexBufferView,
                                            byteOffset = 0,
                                            componentType = Accessor.ComponentType.FLOAT,
                                            count = geometry.cubeCount * 24,
                                            type = Accessor.AccessorType.VEC3,
                                        ),
                                        normal = Accessor(
                                            bufferView = vertexBufferView,
                                            byteOffset = 12,
                                            componentType = Accessor.ComponentType.FLOAT,
                                            count = geometry.cubeCount * 24,
                                            type = Accessor.AccessorType.VEC3,
                                        ),
                                        texcoords = listOf(
                                            Accessor(
                                                bufferView = vertexBufferView,
                                                byteOffset = 24,
                                                componentType = Accessor.ComponentType.FLOAT,
                                                count = geometry.cubeCount * 24,
                                                type = Accessor.AccessorType.VEC2,
                                            ),
                                        ),
                                        joints = listOf(
                                            Accessor(
                                                bufferView = vertexBufferView,
                                                byteOffset = 32,
                                                componentType = Accessor.ComponentType.UNSIGNED_SHORT,
                                                count = geometry.cubeCount * 24,
                                                type = Accessor.AccessorType.VEC4,
                                            ),
                                        ),
                                        weights = listOf(
                                            Accessor(
                                                bufferView = vertexBufferView,
                                                byteOffset = 36,
                                                componentType = Accessor.ComponentType.FLOAT,
                                                count = geometry.cubeCount * 24,
                                                type = Accessor.AccessorType.VEC4,
                                            ),
                                        ),
                                    ),
                                    indices = Accessor(
                                        bufferView = indexBufferView,
                                        componentType = Accessor.ComponentType.UNSIGNED_SHORT,
                                        count = geometry.cubeCount * cubeIndices.size,
                                        type = Accessor.AccessorType.SCALAR,
                                    ),
                                    targets = listOf(),
                                )
                            ),
                            weights = null,
                        )
                    )
                )
            )

            Scene(
                nodes = listOf(meshNode),
            )
        }
        skins = listOf()
    }

    fun load(path: Path): Model {
        loadTextures()
        preprocess(path)
        populateStructure()
        loadGeometries(path)
        buildIndexBuffer()
        assembleScenes()
        return Model(
            scenes = scenes,
            defaultScene = scenes.firstOrNull(),
            skins = skins,
        )
    }
}