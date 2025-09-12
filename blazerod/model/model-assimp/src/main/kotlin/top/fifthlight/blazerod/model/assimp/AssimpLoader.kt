package top.fifthlight.blazerod.model.assimp

import org.joml.Matrix4f
import org.lwjgl.assimp.*
import org.lwjgl.system.MemoryUtil
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.ModelFileLoader.Ability
import top.fifthlight.blazerod.model.util.openChannelCaseInsensitive
import top.fifthlight.blazerod.model.util.readAll
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.jvm.optionals.getOrNull

class AssimpLoadException(message: String) : Exception(message)

class AssimpLoader : ModelFileLoader {
    companion object {
        private val logger = LoggerFactory.getLogger(AssimpLoader::class.java)
    }

    override fun initialize() {
        try {
            Assimp.getLibrary()
            available = true
        } catch (ex: ExceptionInInitializerError) {
            logger.warn("Failed to load assimp library", ex)
        }
    }

    override var available: Boolean = false
        private set

    override val extensions: Map<String, Set<Ability>> = mapOf(
        "3ds" to setOf(Ability.MODEL),
        "ac" to setOf(Ability.MODEL),
        "ase" to setOf(Ability.MODEL),
        "bvh" to setOf(Ability.MODEL),
        "cob" to setOf(Ability.MODEL),
        "csm" to setOf(Ability.MODEL),
        "dae" to setOf(Ability.MODEL),
        "dxf" to setOf(Ability.MODEL),
        "dxf" to setOf(Ability.MODEL),
        "fbx" to setOf(Ability.MODEL),
        "hmp" to setOf(Ability.MODEL),
        "ifc" to setOf(Ability.MODEL),
        "irr" to setOf(Ability.MODEL),
        "irrmesh" to setOf(Ability.MODEL),
        "lwo" to setOf(Ability.MODEL),
        "lws" to setOf(Ability.MODEL),
        "lxo" to setOf(Ability.MODEL),
        "md2" to setOf(Ability.MODEL),
        "md3" to setOf(Ability.MODEL),
        "md5mesh" to setOf(Ability.MODEL),
        "mdc" to setOf(Ability.MODEL),
        "mdl" to setOf(Ability.MODEL),
        "mdl" to setOf(Ability.MODEL),
        "mesh" to setOf(Ability.MODEL),
        "ms3d" to setOf(Ability.MODEL),
        "nff" to setOf(Ability.MODEL),
        "obj" to setOf(Ability.MODEL),
        "off" to setOf(Ability.MODEL),
        "pk3" to setOf(Ability.MODEL),
        "ply" to setOf(Ability.MODEL),
        "ply" to setOf(Ability.MODEL),
        "q3o" to setOf(Ability.MODEL),
        "q3s" to setOf(Ability.MODEL),
        "raw" to setOf(Ability.MODEL),
        "scn" to setOf(Ability.MODEL),
        "smd" to setOf(Ability.MODEL),
        "stl" to setOf(Ability.MODEL),
        "ter" to setOf(Ability.MODEL),
        "usd" to setOf(Ability.MODEL),
        "x" to setOf(Ability.MODEL),
        "xml" to setOf(Ability.MODEL),
    )

    override val probeLength: Int? = null
    override fun probe(buffer: ByteBuffer) = false

    private class Context(
        private val scene: AIScene,
        private val basePath: Path,
    ) : AutoCloseable {
        private val modelUuid = UUID.randomUUID()
        private val skins = mutableListOf<MeshSkinInfo>()
        private var nextNodeIndex = 0
        private val nodeIdMap = mutableMapOf<Long, NodeId>()
        private val meshToSkins = mutableMapOf<Int, Int>()
        private val textures = mutableMapOf<String, Optional<Texture>>()
        private lateinit var materials: List<Material>
        private lateinit var meshes: List<Mesh>

        companion object {
            private val EMPTY_LOAD_RESULT = ModelFileLoader.LoadResult(
                metadata = null,
                model = null,
                animations = listOf(),
            )
        }

        override fun close() {
            Assimp.aiReleaseImport(scene)
        }

        private fun allocateNodeId(node: AINode) = nodeIdMap.getOrPut(node.address()) {
            NodeId(modelUuid, nextNodeIndex++)
        }

        private data class MeshSkinInfo(
            val skin: Skin,
            val nodeToJointIndexMap: Map<Long, Int>,
        )

        private fun loadSkins() {
            val meshes = scene.mMeshes() ?: return
            repeat(scene.mNumMeshes()) { meshIndex ->
                val mesh = AIMesh.create(meshes.get(meshIndex))
                val bonesCount = mesh.mNumBones()
                if (bonesCount == 0) {
                    return@repeat
                }
                val bones = mesh.mBones() ?: return@repeat

                val jointNodeIds = mutableListOf<NodeId>()
                val inverseBindMatrices = mutableListOf<Matrix4f>()
                val nodeToJointIndexMap = mutableMapOf<Long, Int>()
                val jointHumanoidTags = mutableListOf<HumanoidTag?>()
                repeat(bonesCount) { boneIndex ->
                    val bone = AIBone.create(bones.get(boneIndex))
                    val node = bone.mNode()
                    val nodeId = allocateNodeId(node)
                    nodeToJointIndexMap[node.address()] = jointNodeIds.size
                    jointNodeIds += nodeId
                    inverseBindMatrices += bone.mOffsetMatrix().toJoml()
                    jointHumanoidTags += guessHumanoidTagFromName(node.mName().dataString())
                }
                meshToSkins[meshIndex] = skins.size
                skins += MeshSkinInfo(
                    skin = Skin(
                        name = "Skin for #${meshIndex}",
                        joints = jointNodeIds,
                        inverseBindMatrices = inverseBindMatrices,
                        jointHumanoidTags = jointHumanoidTags,
                    ),
                    nodeToJointIndexMap = nodeToJointIndexMap,
                )
            }
        }

        private fun loadTexture(pathString: String) = textures.getOrPut(pathString) {
            try {
                // For Windows & Unix-like path support
                val pathParts = pathString.split('/', '\\')
                val relativePath = if (pathParts.size == 1) {
                    basePath.fileSystem.getPath(pathParts[0])
                } else {
                    basePath.fileSystem.getPath(pathParts[0], *pathParts.subList(1, pathParts.size).toTypedArray())
                }
                val path = basePath.resolve(relativePath)
                val buffer = path.openChannelCaseInsensitive(StandardOpenOption.READ).use { channel ->
                    val size = channel.size()
                    runCatching {
                        channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                    }.getOrNull() ?: run {
                        if (size > 256 * 1024 * 1024) {
                            throw AssimpLoadException("Texture too large! Maximum supported is 256M.")
                        }
                        val size = size.toInt()
                        val buffer = ByteBuffer.allocateDirect(size)
                        channel.readAll(buffer)
                        buffer.flip()
                        buffer
                    }
                }
                Optional.of(
                    Texture(
                        name = pathString,
                        bufferView = BufferView(
                            buffer = Buffer(
                                name = "Texture $pathString",
                                buffer = buffer,
                            ),
                            byteLength = buffer.remaining(),
                            byteOffset = 0,
                            byteStride = 0,
                        ),
                        sampler = Texture.Sampler(),
                    )
                )
            } catch (ex: Exception) {
                logger.warn("Failed to load PMX texture", ex)
                return Optional.empty<Texture>()
            }
        }

        private fun loadMaterials() {
            val materials = scene.mMaterials() ?: run {
                materials = listOf()
                return
            }
            this.materials = (0 until scene.mNumMaterials()).map { index ->
                val materialPtr = materials[index]
                val material = AIMaterial.create(materialPtr)

                val diffuseColor = material.getColor(Assimp.AI_MATKEY_COLOR_DIFFUSE, 0, 0)
                val diffuseTexture = material.getTexturePath(Assimp.aiTextureType_DIFFUSE, 0)

                Material.Unlit(
                    name = "Material #${index}",
                    baseColor = diffuseColor ?: RgbaColor(1f, 1f, 1f, 1f),
                    baseColorTexture = diffuseTexture
                        ?.let { loadTexture(it).getOrNull() }
                        ?.let { texture ->
                            Material.TextureInfo(
                                texture = texture,
                            )
                        },
                )
            }
        }

        private fun loadMeshes() {
            val meshes = scene.mMeshes() ?: run {
                meshes = listOf()
                return
            }
            this.meshes = (0 until scene.mNumMeshes()).map { index ->
                val meshPtr = meshes[index]
                val mesh = AIMesh.create(meshPtr)

                /*
                    struct aiVector3D {
                        float x;
                        float y;
                        float z;
                    }
                    Used for vertices and normals
                */
                val vertices = mesh.mVertices()
                val verticesLength = mesh.mNumVertices() * vertices.sizeof()
                val vertexBuffer = ByteBuffer.allocateDirect(verticesLength).order(ByteOrder.nativeOrder())
                MemoryUtil.memCopy(vertices.address(), MemoryUtil.memAddress(vertexBuffer), verticesLength.toLong())

                val normalBuffer = run {
                    val normals = mesh.mNormals() ?: return@run null
                    val normalsLength = mesh.mNumVertices() * normals.sizeof()
                    val normalBuffer = ByteBuffer.allocateDirect(normalsLength).order(ByteOrder.nativeOrder())
                    MemoryUtil.memCopy(normals.address(), MemoryUtil.memAddress(normalBuffer), normalsLength.toLong())
                    normalBuffer
                }

                val texCoordBuffer = run {
                    // This buffer is Vector3! we need to convert it to vec2
                    val texCoord = mesh.mTextureCoords(0) ?: return@run null
                    val texCoordLength = mesh.mNumVertices() * 8
                    val texCoordBuffer = ByteBuffer.allocateDirect(texCoordLength).order(ByteOrder.nativeOrder())
                    val bufferAddress = MemoryUtil.memAddress(texCoordBuffer)
                    repeat(mesh.mNumVertices()) { vertexIndex ->
                        MemoryUtil.memCopy(texCoord.address(vertexIndex), bufferAddress + vertexIndex * 8, 8L)
                    }
                    texCoordBuffer
                }

                val skin = meshToSkins[index]?.let { skins.getOrNull(it) }
                val bonesCount = mesh.mNumBones()
                val bones = mesh.mBones()
                var jointsBuffer: ByteBuffer? = null
                var weightsBuffer: ByteBuffer? = null
                if (skin != null && bonesCount != 0 && bones != null) {
                    val maxBonesPerVertex = 4
                    jointsBuffer = ByteBuffer.allocateDirect(mesh.mNumVertices() * maxBonesPerVertex * 2)
                        .order(ByteOrder.nativeOrder())
                    weightsBuffer = ByteBuffer.allocateDirect(mesh.mNumVertices() * maxBonesPerVertex * 4)
                        .order(ByteOrder.nativeOrder())
                    val boneInfluenceCount = IntArray(mesh.mNumVertices())
                    repeat(bonesCount) { boneIndex ->
                        val bone = AIBone.create(bones.get(boneIndex))
                        val node = bone.mNode()
                        val jointIndex = skin.nodeToJointIndexMap[node.address()] ?: return@repeat
                        val weights = bone.mWeights()
                        val weightsCount = bone.mNumWeights()
                        if (weightsCount != 0 && weights != null) {
                            repeat(weightsCount) { weightIndex ->
                                val vertexWeightAddress = weights.address(weightIndex)
                                val vertexId = AIVertexWeight.nmVertexId(vertexWeightAddress)
                                val weight = AIVertexWeight.nmWeight(vertexWeightAddress)

                                val jointVertexIndex = boneInfluenceCount[vertexId]
                                if (jointVertexIndex < maxBonesPerVertex) {
                                    jointsBuffer.putShort(
                                        (vertexId * maxBonesPerVertex + jointVertexIndex) * 2,
                                        jointIndex.toShort()
                                    )
                                    weightsBuffer.putFloat(
                                        (vertexId * maxBonesPerVertex + jointVertexIndex) * 4,
                                        weight
                                    )
                                    boneInfluenceCount[vertexId]++
                                }
                            }
                        }
                    }
                }

                val faces = mesh.mFaces()
                val facesCount = mesh.mNumFaces()
                val indexSize = facesCount * 12
                val indexBuffer = ByteBuffer.allocateDirect(indexSize).order(ByteOrder.nativeOrder())
                val indexAddress = MemoryUtil.memAddress(indexBuffer)
                var indexOffset = 0
                repeat(facesCount) {
                    /*
                        struct aiFace {
                            unsigned int mNumIndices;
                            unsigned int * mIndices;
                        }
                     */
                    // AVOID OBJECT ALLOCATION!
                    val faceAddress = faces.address(it)
                    if (faceAddress == 0L) {
                        throw NullPointerException("Face #$it of mesh $index is null")
                    }
                    val numIndices = AIFace.nmNumIndices(faceAddress)

                    val indexArrayAddress = MemoryUtil.memGetAddress(faceAddress + AIFace.MINDICES)
                    if (indexArrayAddress == 0L) {
                        throw NullPointerException("Index array of face $it in mesh $index is null")
                    }
                    val indexDataLength = numIndices * 4
                    Objects.checkFromIndexSize(indexOffset, indexDataLength, indexSize)
                    MemoryUtil.memCopy(indexArrayAddress, indexAddress + indexOffset, indexDataLength.toLong())
                    indexOffset += indexDataLength
                }

                val primitive = Primitive(
                    mode = Primitive.Mode.TRIANGLES,
                    material = materials.getOrNull(mesh.mMaterialIndex())
                        ?: throw AssimpLoadException("No material #${mesh.mMaterialIndex()} for mesh #${index}"),
                    attributes = Primitive.Attributes.Primitive(
                        position = Accessor(
                            bufferView = BufferView(
                                buffer = Buffer(
                                    name = "Position",
                                    buffer = vertexBuffer,
                                ),
                                byteLength = verticesLength,
                                byteOffset = 0,
                                byteStride = 0,
                            ),
                            componentType = Accessor.ComponentType.FLOAT,
                            type = Accessor.AccessorType.VEC3,
                            count = mesh.mNumVertices(),
                        ),
                        normal = normalBuffer?.let {
                            Accessor(
                                bufferView = BufferView(
                                    buffer = Buffer(
                                        name = "Normal",
                                        buffer = it,
                                    ),
                                    byteLength = normalBuffer.remaining(),
                                    byteOffset = 0,
                                    byteStride = 0,
                                ),
                                componentType = Accessor.ComponentType.FLOAT,
                                type = Accessor.AccessorType.VEC3,
                                count = mesh.mNumVertices(),
                            )
                        },
                        texcoords = texCoordBuffer?.let {
                            listOf(
                                Accessor(
                                    bufferView = BufferView(
                                        buffer = Buffer(
                                            name = "TexCoord",
                                            buffer = it,
                                        ),
                                        byteLength = texCoordBuffer.remaining(),
                                        byteOffset = 0,
                                        byteStride = 0,
                                    ),
                                    componentType = Accessor.ComponentType.FLOAT,
                                    type = Accessor.AccessorType.VEC2,
                                    count = mesh.mNumVertices(),
                                )
                            )
                        } ?: listOf(),
                        joints = jointsBuffer?.let {
                            listOf(
                                Accessor(
                                    bufferView = BufferView(
                                        buffer = Buffer(
                                            name = "Joints",
                                            buffer = jointsBuffer,
                                        ),
                                        byteLength = jointsBuffer.remaining(),
                                        byteOffset = 0,
                                        byteStride = 0,
                                    ),
                                    componentType = Accessor.ComponentType.SHORT,
                                    type = Accessor.AccessorType.VEC4,
                                    count = mesh.mNumVertices(),
                                )
                            )
                        } ?: listOf(),
                        weights = weightsBuffer?.let {
                            listOf(
                                Accessor(
                                    bufferView = BufferView(
                                        buffer = Buffer(
                                            name = "Weights",
                                            buffer = weightsBuffer,
                                        ),
                                        byteLength = weightsBuffer.remaining(),
                                        byteOffset = 0,
                                        byteStride = 0,
                                    ),
                                    componentType = Accessor.ComponentType.FLOAT,
                                    type = Accessor.AccessorType.VEC4,
                                    count = mesh.mNumVertices(),
                                )
                            )
                        } ?: listOf(),
                    ),
                    indices = Accessor(
                        bufferView = BufferView(
                            buffer = Buffer(
                                name = "Indices",
                                buffer = indexBuffer,
                            ),
                            byteLength = indexSize,
                            byteOffset = 0,
                            byteStride = 0,
                        ),
                        componentType = Accessor.ComponentType.UNSIGNED_INT,
                        type = Accessor.AccessorType.SCALAR,
                        count = mesh.mNumFaces() * 3,
                    ),
                    targets = listOf(),
                )

                Mesh(
                    id = MeshId(modelUuid, index),
                    primitives = listOf(primitive),
                    weights = null,
                )
            }
        }

        private fun loadNode(node: AINode): Node {
            val children = node.mChildren()
            return Node(
                name = node.mName().dataString(),
                id = allocateNodeId(node),
                transform = NodeTransform.Matrix(node.mTransformation().toJoml()),
                components = buildList {
                    val meshIndices = node.mMeshes()
                    meshIndices?.let { meshIndices ->
                        repeat(node.mNumMeshes()) { index ->
                            val meshIndex = meshIndices.get(index)
                            val mesh = meshes.getOrNull(meshIndex)
                                ?: throw AssimpLoadException("No mesh #$meshIndex for node")
                            add(NodeComponent.MeshComponent(mesh))

                            meshToSkins[meshIndex]?.let { skins.getOrNull(it) }?.let { skin ->
                                add(
                                    NodeComponent.SkinComponent(
                                        skin = skin.skin,
                                        meshIds = listOf(mesh.id),
                                    )
                                )
                            }
                        }
                    }
                },
                children = children?.let { children ->
                    (0 until node.mNumChildren()).map { nodeIndex ->
                        val node = AINode.create(children.get(nodeIndex))
                        loadNode(node)
                    }
                } ?: listOf()
            )
        }

        fun load(): ModelFileLoader.LoadResult {
            loadSkins()
            loadMaterials()
            loadMeshes()
            val rootNode = scene.mRootNode()?.let { loadNode(it) } ?: return EMPTY_LOAD_RESULT
            val scene = Scene(nodes = listOf(rootNode))
            val model = Model(
                scenes = listOf(scene),
                defaultScene = scene,
                skins = skins.map { it.skin },
                expressions = listOf(),
            )
            return ModelFileLoader.LoadResult(
                metadata = null,
                model = model,
                animations = listOf(),
            )
        }
    }

    override fun load(
        path: Path,
        basePath: Path,
    ): ModelFileLoader.LoadResult {
        val flags = Assimp.aiProcess_Triangulate or
                Assimp.aiProcess_FlipUVs or
                Assimp.aiProcess_LimitBoneWeights or
                Assimp.aiProcess_PopulateArmatureData
        return Context(
            scene = Assimp.aiImportFile(path.toString(), flags)
                ?: throw AssimpLoadException(Assimp.aiGetErrorString() ?: "Unknown error"),
            basePath = basePath,
        ).use {
            it.load()
        }
    }
}