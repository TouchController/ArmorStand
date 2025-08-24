package top.fifthlight.armorstand.vmc

import kotlinx.atomicfu.locks.withLock
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc
import top.fifthlight.blazerod.model.Expression
import top.fifthlight.blazerod.model.HumanoidTag
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

sealed class VmcMarionetteStateView {
    abstract val rootTransform: RootTransform?
    abstract val boneTransforms: Map<HumanoidTag, BoneTransform>
    abstract val blendShapes: Map<Expression.Tag, Float>

    abstract class RootTransform {
        abstract val position: Vector3fc
        abstract val rotation: Quaternionfc
    }

    abstract class BoneTransform {
        abstract val position: Vector3fc
        abstract val rotation: Quaternionfc
    }

    abstract fun snapshot(): VmcMarionetteStateView
}

class ImmutableVmcMarionetteStateView(
    rootTransform: VmcMarionetteStateView.RootTransform? = null,
    boneTransforms: Map<HumanoidTag, VmcMarionetteStateView.BoneTransform> = mapOf(),
    blendShapes: Map<Expression.Tag, Float> = mapOf(),
) : VmcMarionetteStateView() {
    override val rootTransform = rootTransform?.let { RootTransform(it) }
    override val boneTransforms = boneTransforms.mapValues { BoneTransform(it.value) }
    override val blendShapes = blendShapes.toMap()

    class RootTransform(
        position: Vector3fc,
        rotation: Quaternionfc,
    ) : VmcMarionetteStateView.RootTransform() {
        constructor(transform: VmcMarionetteStateView.RootTransform) : this(
            position = transform.position,
            rotation = transform.rotation,
        )

        override val position: Vector3fc = Vector3f(position)
        override val rotation: Quaternionfc = Quaternionf(rotation)
    }

    class BoneTransform(
        position: Vector3fc,
        rotation: Quaternionfc,
    ) : VmcMarionetteStateView.BoneTransform() {
        constructor(transform: VmcMarionetteStateView.BoneTransform) : this(
            position = transform.position,
            rotation = transform.rotation,
        )

        override val position: Vector3fc = Vector3f(position)
        override val rotation: Quaternionfc = Quaternionf(rotation)
    }

    override fun snapshot() = this
}

interface VmcMarionetteStateWriter {
    fun setRootTransform(
        posX: Float,
        posY: Float,
        posZ: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        rotW: Float,
    )

    fun setBoneTransform(
        tag: HumanoidTag,
        posX: Float,
        posY: Float,
        posZ: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        rotW: Float,
    )

    fun setBlendShape(tag: Expression.Tag, weight: Float)
}

sealed class MutableVmcMarionetteStateView : VmcMarionetteStateView(), VmcMarionetteStateWriter {
    abstract override val rootTransform: RootTransform?
    abstract override val boneTransforms: MutableMap<HumanoidTag, out BoneTransform>
    abstract override val blendShapes: MutableMap<Expression.Tag, Float>
}

private class MutableVmcMarionetteStateViewImpl : MutableVmcMarionetteStateView() {
    override var rootTransform: RootTransform? = null
    override val boneTransforms: MutableMap<HumanoidTag, BoneTransform> = mutableMapOf()
    override val blendShapes: MutableMap<Expression.Tag, Float> = mutableMapOf()

    class RootTransform : VmcMarionetteStateView.RootTransform() {
        override var position = Vector3f()
        override var rotation = Quaternionf()
    }

    class BoneTransform : VmcMarionetteStateView.BoneTransform() {
        override var position = Vector3f()
        override var rotation = Quaternionf()
    }

    override fun setRootTransform(
        posX: Float,
        posY: Float,
        posZ: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        rotW: Float,
    ) {
        val rootTransform = rootTransform ?: RootTransform().also { this.rootTransform = it }
        rootTransform.position.set(posX, posY, posZ)
        rootTransform.rotation.set(rotX, rotY, rotZ, rotW)
    }

    override fun setBoneTransform(
        tag: HumanoidTag,
        posX: Float,
        posY: Float,
        posZ: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        rotW: Float,
    ) {
        val boneTransforms = boneTransforms.getOrPut(tag) { BoneTransform() }
        boneTransforms.position.set(posX, posY, posZ)
        boneTransforms.rotation.set(rotX, rotY, rotZ, rotW)
    }

    override fun setBlendShape(tag: Expression.Tag, weight: Float) {
        blendShapes[tag] = weight
    }

    override fun snapshot() = ImmutableVmcMarionetteStateView(rootTransform, boneTransforms, blendShapes)
}

class VmcMarionetteStateHolder {
    private val writeLock = ReentrantLock()
    private val writeBuffer = MutableVmcMarionetteStateViewImpl()
    private val readBuffer = AtomicReference<VmcMarionetteStateView>(writeBuffer.snapshot())

    fun read(): VmcMarionetteStateView = readBuffer.get()

    fun write(block: VmcMarionetteStateWriter.() -> Unit) = writeLock.withLock {
        block(writeBuffer)
        readBuffer.set(writeBuffer.snapshot())
    }
}
