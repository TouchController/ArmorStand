package top.fifthlight.blazerod.model.vmd

import it.unimi.dsi.fastutil.bytes.ByteArrayList
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntIterable
import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.ModelFileLoader
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.model.animation.*
import top.fifthlight.blazerod.model.util.MMD_SCALE
import top.fifthlight.blazerod.model.util.MutableFloat
import top.fifthlight.blazerod.model.util.readAll
import top.fifthlight.blazerod.model.util.toRadian
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class VmdLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

private inline fun IntIterable.forEachInt(action: (Int) -> Unit) {
    val iterator = intIterator()
    while (iterator.hasNext()) {
        action(iterator.nextInt())
    }
}

class VmdLoader : ModelFileLoader {
    companion object {
        private val OLD_VMD_SIGNATURE = "Vocaloid Motion Data file".toByteArray()
        private val NEW_VMD_SIGNATURE = "Vocaloid Motion Data 0002".toByteArray()
        private val VMD_SIGNATURES = listOf(OLD_VMD_SIGNATURE, NEW_VMD_SIGNATURE)

        private const val FRAME_TIME_SEC = 1f / 30f
    }

    override val extensions = mapOf(
        "vmd" to setOf(ModelFileLoader.Ability.EXTERNAL_ANIMATION),
    )
    override val probeLength = VMD_SIGNATURES.maxOf { it.size }

    override fun probe(buffer: ByteBuffer) = VMD_SIGNATURES.any { signature ->
        val lastPosition = buffer.position()
        if (buffer.remaining() < signature.size) return false
        val signatureBytes = ByteArray(signature.size)
        buffer.get(signatureBytes, 0, signature.size)
        buffer.position(lastPosition)
        signatureBytes.contentEquals(signature)
    }

    private val SHIFT_JIS = Charset.forName("Shift-JIS")
    private val decoder = SHIFT_JIS.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private fun loadString(buffer: ByteBuffer, maxLength: Int): String {
        val bytes = ByteBuffer.allocate(maxLength)
        bytes.put(buffer.slice(buffer.position(), maxLength))
        buffer.position(buffer.position() + maxLength)
        val nullIndex = (0 until maxLength)
            .indexOfFirst { bytes.get(it) == 0.toByte() }
            .takeIf { it != -1 } ?: maxLength
        val stringBytes = bytes.slice(0, nullIndex).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            decoder.decode(stringBytes).toString()
        } catch (ex: Exception) {
            val hexBytes = (0 until nullIndex).joinToString("") {
                "%02X".format(bytes.get(it))
            }
            throw VmdLoadException("Failed to decode string for byte $hexBytes", ex)
        }
    }

    private fun loadHeader(buffer: ByteBuffer) {
        val signature = ByteArray(30)
        buffer.get(signature)
        fun compareSignature(target: ByteArray, source: ByteArray): Boolean {
            if (target.size < source.size) {
                return false
            }
            return (0 until source.size).all {
                source[it] == target[it]
            }
        }

        val isNewFormat = when {
            compareSignature(signature, OLD_VMD_SIGNATURE) -> false
            compareSignature(signature, NEW_VMD_SIGNATURE) -> true
            else -> throw VmdLoadException("Bad VMD file signature")
        }

        if (isNewFormat) {
            buffer.position(buffer.position() + 20)
        } else {
            buffer.position(buffer.position() + 10)
        }
    }

    private data class ChannelData(
        val frameIndexer: AnimationKeyFrameIndexer,
        val translation: AnimationKeyFrameData<Vector3f>,
        val rotation: AnimationKeyFrameData<Quaternionf>,
        val translationCurve: VmdBezierChannelComponent,
        val rotationCurve: VmdBezierChannelComponent,
    )

    private class BoneChannel {
        val frameList = IntArrayList()
        val translationData = FloatArrayList()
        val rotationData = FloatArrayList()
        val translationCurveData = ByteArrayList()
        val rotationCurveData = ByteArrayList()

        fun toData(): ChannelData {
            val indices = IntArrayList(frameList.size)
            repeat(frameList.size) { indices.add(it) }
            indices.sort { a, b -> frameList.getInt(a) - frameList.getInt(b) }

            val sortedTimeList = FloatArrayList(frameList.size)
            val sortedTranslationList = FloatArrayList(translationData.size)
            val sortedRotationList = FloatArrayList(rotationData.size)
            val sortedTranslationCurveList = ByteArrayList(translationCurveData.size)
            val sortedRotationCurveList = ByteArrayList(rotationCurveData.size)
            indices.forEachInt { i ->
                sortedTimeList.add(frameList.getInt(i) * FRAME_TIME_SEC)
                repeat(3) {
                    sortedTranslationList.add(translationData.getFloat(i * 3 + it))
                }
                repeat(4) {
                    sortedRotationList.add(rotationData.getFloat(i * 4 + it))
                }
                repeat(12) {
                    sortedTranslationCurveList.add(translationCurveData.getByte(i * 12 + it))
                }
                repeat(4) {
                    sortedRotationCurveList.add(rotationCurveData.getByte(i * 4 + it))
                }
            }

            return ChannelData(
                frameIndexer = ListAnimationKeyFrameIndexer(sortedTimeList),
                translation = AnimationKeyFrameData.ofVector3f(sortedTranslationList, 1),
                rotation = AnimationKeyFrameData.ofQuaternionf(sortedRotationList, 1),
                translationCurve = VmdBezierChannelComponent(
                    values = sortedTranslationCurveList,
                    frames = sortedTimeList.size,
                    channels = 3,
                    cameraOrder = false,
                ),
                rotationCurve = VmdBezierChannelComponent(
                    values = sortedRotationCurveList,
                    frames = sortedTimeList.size,
                    channels = 1,
                    cameraOrder = false,
                ),
            )
        }
    }

    private fun loadBone(buffer: ByteBuffer): List<KeyFrameAnimationChannel<*, *>> {
        val channels = mutableMapOf<String, BoneChannel>()
        val boneKeyframeCount = buffer.getInt()

        repeat(boneKeyframeCount) {
            val boneName = loadString(buffer, 15)
            val channel = channels.getOrPut(boneName, ::BoneChannel)

            val frameNumber = buffer.getInt()
            channel.frameList.add(frameNumber)
            // translation, invert X axis
            channel.translationData.add(buffer.getFloat() * -MMD_SCALE)
            channel.translationData.add(buffer.getFloat() * MMD_SCALE)
            channel.translationData.add(buffer.getFloat() * MMD_SCALE)
            // rotation, invert Y and Z
            channel.rotationData.add(buffer.getFloat())
            channel.rotationData.add(-buffer.getFloat())
            channel.rotationData.add(-buffer.getFloat())
            channel.rotationData.add(buffer.getFloat())

            // translation curve data
            repeat(12) {
                channel.translationCurveData.add(buffer.get())
            }
            // rotation curve data
            repeat(4) {
                channel.rotationCurveData.add(buffer.get())
            }
            buffer.position(buffer.position() + 48)
        }

        return channels.flatMap { (name, channel) ->
            val channelData = channel.toData()
            listOf(
                VmdBezierVector3fInterpolation().let { interpolation ->
                    KeyFrameAnimationChannel(
                        type = AnimationChannel.Type.Translation,
                        typeData = AnimationChannel.Type.TransformData(
                            node = AnimationChannel.Type.NodeData(
                                targetNode = null,
                                targetNodeName = name,
                                targetHumanoidTag = HumanoidTag.fromPmxJapanese(name),
                            ),
                            transformId = TransformId.RELATIVE_ANIMATION,
                        ),
                        components = listOf(channelData.translationCurve, interpolation),
                        indexer = channelData.frameIndexer,
                        keyframeData = channelData.translation,
                        interpolation = interpolation,
                    )
                },
                VmdBezierQuaternionfInterpolation().let { interpolation ->
                    KeyFrameAnimationChannel(
                        type = AnimationChannel.Type.Rotation,
                        typeData = AnimationChannel.Type.TransformData(
                            node = AnimationChannel.Type.NodeData(
                                targetNode = null,
                                targetNodeName = name,
                                targetHumanoidTag = HumanoidTag.fromPmxJapanese(name),
                            ),
                            transformId = TransformId.RELATIVE_ANIMATION,
                        ),
                        components = listOf(channelData.rotationCurve, interpolation),
                        indexer = channelData.frameIndexer,
                        keyframeData = channelData.rotation,
                        interpolation = interpolation,
                    )
                },
            )
        }
    }

    private class WeightChannel {
        val frameList = IntArrayList()
        val weightList = FloatArrayList()

        fun toData(): Pair<AnimationKeyFrameIndexer, AnimationKeyFrameData<MutableFloat>> {
            val indices = IntArrayList(frameList.size)
            repeat(frameList.size) { indices.add(it) }
            indices.sort { a, b -> frameList.getInt(a) - frameList.getInt(b) }

            val sortedTimeList = FloatArrayList(frameList.size)
            val sortedWeightList = FloatArrayList(weightList.size)

            indices.forEachInt { i ->
                sortedTimeList.add(frameList.getInt(i) * FRAME_TIME_SEC)
                sortedWeightList.add(weightList.getFloat(i))
            }

            return Pair(
                ListAnimationKeyFrameIndexer(sortedTimeList),
                AnimationKeyFrameData.ofFloat(sortedWeightList, 1)
            )
        }
    }

    private fun loadFace(buffer: ByteBuffer): List<KeyFrameAnimationChannel<*, *>> {
        val channels = mutableMapOf<String, WeightChannel>()
        val faceKeyframeCount = buffer.getInt()
        repeat(faceKeyframeCount) {
            val faceName = loadString(buffer, 15)
            val channel = channels.getOrPut(faceName, ::WeightChannel)

            val frameNumber = buffer.getInt()
            channel.frameList.add(frameNumber)

            val weight = buffer.getFloat()
            channel.weightList.add(weight)
        }

        return channels.flatMap { (name, channel) ->
            val (indexer, data) = channel.toData()
            listOf(
                KeyFrameAnimationChannel(
                    type = AnimationChannel.Type.Expression,
                    typeData = AnimationChannel.Type.ExpressionData(name = name),
                    indexer = indexer,
                    keyframeData = data,
                    interpolation = AnimationInterpolation.linear,
                ),
            )
        }
    }

    private fun loadCamera(buffer: ByteBuffer): List<KeyFrameAnimationChannel<*, *>> {
        val cameraKeyframeCount = buffer.getInt().takeIf { it > 0 } ?: return emptyList()
        val frameList = IntArrayList()
        val distanceList = FloatArrayList()
        val positionList = FloatArrayList()
        val rotationList = FloatArrayList()
        val fovList = FloatArrayList()
        val distanceCurveData = ByteArrayList()
        val positionCurveData = ByteArrayList()
        val rotationCurveData = ByteArrayList()
        val fovCurveData = ByteArrayList()

        repeat(cameraKeyframeCount) {
            frameList.add(buffer.getInt())
            distanceList.add(buffer.getFloat() * MMD_SCALE)

            // XYZ, invert X
            positionList.add(-buffer.getFloat() * MMD_SCALE)
            positionList.add(buffer.getFloat() * MMD_SCALE)
            positionList.add(buffer.getFloat() * MMD_SCALE)

            // Invert Y
            rotationList.add(buffer.getFloat())
            rotationList.add(Math.PI.toFloat() + buffer.getFloat())
            rotationList.add(buffer.getFloat())

            repeat(4) {
                distanceCurveData.add(buffer.get())
            }
            repeat(12) {
                positionCurveData.add(buffer.get())
            }
            repeat(4) {
                rotationCurveData.add(buffer.get())
            }
            repeat(4) {
                fovCurveData.add(buffer.get())
            }

            fovList.add(buffer.getInt().toUInt().toFloat().toRadian())

            // Skip perspective
            buffer.position(buffer.position() + 1)
        }

        val indices = IntArrayList(frameList.size)
        repeat(frameList.size) { indices.add(it) }
        indices.sort { a, b -> frameList.getInt(a) - frameList.getInt(b) }

        val sortedTimeList = FloatArrayList(frameList.size)
        val sortedDistanceList = FloatArrayList(distanceList.size)
        val sortedPositionList = FloatArrayList(positionList.size)
        val sortedRotationList = FloatArrayList(rotationList.size)
        val sortedFovList = FloatArrayList(fovList.size)
        val sortedDistanceCurveData = ByteArrayList(distanceCurveData.size)
        val sortedPositionCurveData = ByteArrayList(positionCurveData.size)
        val sortedRotationCurveData = ByteArrayList(rotationCurveData.size)
        val sortedFovCurveData = ByteArrayList(fovCurveData.size)

        indices.forEachInt { i ->
            sortedTimeList.add(frameList.getInt(i) * FRAME_TIME_SEC)
            sortedDistanceList.add(distanceList.getFloat(i))
            repeat(3) {
                sortedPositionList.add(positionList.getFloat(i * 3 + it))
            }
            repeat(3) {
                sortedRotationList.add(rotationList.getFloat(i * 3 + it))
            }
            sortedFovList.add(fovList.getFloat(i))
            repeat(4) {
                sortedDistanceCurveData.add(distanceCurveData.getByte(i * 4 + it))
            }
            repeat(12) {
                sortedPositionCurveData.add(positionCurveData.getByte(i * 12 + it))
            }
            repeat(4) {
                sortedRotationCurveData.add(rotationCurveData.getByte(i * 4 + it))
            }
            repeat(4) {
                sortedFovCurveData.add(fovCurveData.getByte(i * 4 + it))
            }
        }

        return listOf(
            VmdBezierFloatInterpolation().let { interpolation ->
                KeyFrameAnimationChannel(
                    type = AnimationChannel.Type.MMDCameraDistance,
                    typeData = AnimationChannel.Type.CameraData(cameraName = "MMD Camera"),
                    components = listOf(
                        VmdBezierChannelComponent(
                            values = sortedDistanceCurveData,
                            frames = sortedTimeList.size,
                            channels = 1,
                            cameraOrder = true,
                        ),
                        interpolation,
                    ),
                    indexer = ListAnimationKeyFrameIndexer(sortedTimeList),
                    keyframeData = AnimationKeyFrameData.ofFloat(sortedDistanceList, 1),
                    interpolation = interpolation,
                )
            },
            VmdBezierVector3fInterpolation().let { interpolation ->
                KeyFrameAnimationChannel(
                    type = AnimationChannel.Type.MMDCameraTarget,
                    typeData = AnimationChannel.Type.CameraData(cameraName = "MMD Camera"),
                    components = listOf(
                        VmdBezierChannelComponent(
                            values = sortedPositionCurveData,
                            frames = sortedTimeList.size,
                            channels = 3,
                            cameraOrder = true,
                        ),
                        interpolation,
                    ),
                    indexer = ListAnimationKeyFrameIndexer(sortedTimeList),
                    keyframeData = AnimationKeyFrameData.ofVector3f(sortedPositionList, 1),
                    interpolation = interpolation,
                )
            },
            VmdBezierSimpleVector3fInterpolation().let { interpolation ->
                KeyFrameAnimationChannel(
                    type = AnimationChannel.Type.MMDCameraRotation,
                    typeData = AnimationChannel.Type.CameraData(cameraName = "MMD Camera"),
                    components = listOf(
                        VmdBezierChannelComponent(
                            values = sortedRotationCurveData,
                            frames = sortedTimeList.size,
                            channels = 1,
                            cameraOrder = true,
                        ),
                        interpolation,
                    ),
                    indexer = ListAnimationKeyFrameIndexer(sortedTimeList),
                    keyframeData = AnimationKeyFrameData.ofVector3f(sortedRotationList, 1),
                    interpolation = interpolation,
                )
            },
            VmdBezierFloatInterpolation().let { interpolation ->
                KeyFrameAnimationChannel(
                    type = AnimationChannel.Type.CameraFov,
                    typeData = AnimationChannel.Type.CameraData(cameraName = "MMD Camera"),
                    components = listOf(
                        VmdBezierChannelComponent(
                            values = sortedFovCurveData,
                            frames = sortedTimeList.size,
                            channels = 1,
                            cameraOrder = true,
                        ),
                        interpolation,
                    ),
                    indexer = ListAnimationKeyFrameIndexer(sortedTimeList),
                    keyframeData = AnimationKeyFrameData.ofFloat(sortedFovList, 1),
                    interpolation = interpolation,
                )
            },
        )
    }

    private fun load(buffer: ByteBuffer): ModelFileLoader.LoadResult {
        loadHeader(buffer)
        val boneChannels = loadBone(buffer)
        val faceChannels = if (buffer.hasRemaining()) {
            loadFace(buffer)
        } else {
            listOf()
        }
        val cameraChannels = if (buffer.hasRemaining()) {
            loadCamera(buffer)
        } else {
            listOf()
        }

        return ModelFileLoader.LoadResult(
            metadata = null,
            model = null,
            animations = listOf(SimpleAnimation(channels = boneChannels + faceChannels + cameraChannels)),
        )
    }

    override fun load(
        path: Path,
        basePath: Path,
    ) = FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        val fileSize = channel.size()
        val buffer = runCatching {
            channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
        }.getOrNull() ?: run {
            if (fileSize > 32 * 1024 * 1024) {
                throw VmdLoadException("VMD animation size too large: maximum allowed is 32M, current is $fileSize")
            }
            val fileSize = fileSize.toInt()
            val buffer = ByteBuffer.allocate(fileSize)
            channel.readAll(buffer)
            buffer.flip()
            buffer
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        load(buffer)
    }
}