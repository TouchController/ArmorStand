package top.fifthlight.blazerod.layout

import it.unimi.dsi.fastutil.floats.AbstractFloatList
import it.unimi.dsi.fastutil.floats.FloatList
import it.unimi.dsi.fastutil.ints.AbstractIntList
import it.unimi.dsi.fastutil.ints.IntList
import org.joml.*
import top.fifthlight.blazerod.model.RgbaColor
import java.nio.ByteBuffer
import java.util.Objects.checkIndex
import kotlin.reflect.KProperty

abstract class GpuDataLayout<L : GpuDataLayout<L>> {
    private var offset = 0
    private var buffer: ByteBuffer? = null
    abstract val strategy: LayoutStrategy

    val totalSize: Int
        get() = offset

    sealed class Field<T>(layout: GpuDataLayout<*>, align: Int, size: Int) {
        val offset: Int

        operator fun setValue(thisRef: GpuDataLayout<*>, property: KProperty<*>, value: T) {
            setValue(checkNotNull(thisRef.buffer) { "No buffer bound to layout" }, value)
        }

        operator fun getValue(thisRef: GpuDataLayout<*>, property: KProperty<*>): T =
            getValue(checkNotNull(thisRef.buffer) { "No buffer bound to layout" })

        abstract fun setValue(buffer: ByteBuffer, value: T)
        abstract fun getValue(buffer: ByteBuffer): T

        init {
            val padding = when (val remainder = layout.offset % align) {
                0 -> 0
                else -> align - remainder
            }
            offset = layout.offset + padding
            layout.offset += padding + size
        }

        class IntField(layout: GpuDataLayout<*>) : Field<Int>(
            layout = layout,
            align = layout.strategy.scalarAlign,
            size = layout.strategy.scalarSize,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Int) {
                buffer.putInt(offset, value)
            }

            override fun getValue(buffer: ByteBuffer) = buffer.getInt(offset)
        }

        class UIntField(layout: GpuDataLayout<*>) : Field<UInt>(
            layout = layout,
            align = layout.strategy.scalarAlign,
            size = layout.strategy.scalarSize,
        ) {
            override fun setValue(buffer: ByteBuffer, value: UInt) {
                buffer.putInt(offset, value.toInt())
            }

            override fun getValue(buffer: ByteBuffer) = buffer.getInt(offset).toUInt()
        }

        class FloatField(layout: GpuDataLayout<*>) : Field<Float>(
            layout = layout,
            align = layout.strategy.scalarAlign,
            size = layout.strategy.scalarSize,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Float) {
                buffer.putFloat(offset, value)
            }

            override fun getValue(buffer: ByteBuffer) = buffer.getFloat(offset)
        }

        class Vec2Field(layout: GpuDataLayout<*>) : Field<Vector2fc>(
            layout = layout,
            align = layout.strategy.vec2Align,
            size = layout.strategy.vec2Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector2fc) {
                value.get(offset, buffer)
            }

            private val vector = Vector2f()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class Vec3Field(layout: GpuDataLayout<*>) : Field<Vector3fc>(
            layout = layout,
            align = layout.strategy.vec3Align,
            size = layout.strategy.vec3Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector3fc) {
                value.get(offset, buffer)
            }

            private val vector = Vector3f()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class Vec4Field(layout: GpuDataLayout<*>) : Field<Vector4fc>(
            layout = layout,
            align = layout.strategy.vec4Align,
            size = layout.strategy.vec4Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector4fc) {
                value.get(offset, buffer)
            }

            private val vector = Vector4f()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class RgbaColorField(layout: GpuDataLayout<*>) : Field<RgbaColor>(
            layout = layout,
            align = layout.strategy.vec4Align,
            size = layout.strategy.vec4Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: RgbaColor) {
                buffer.putFloat(offset + 0, value.r)
                buffer.putFloat(offset + 4, value.g)
                buffer.putFloat(offset + 8, value.b)
                buffer.putFloat(offset + 12, value.a)
            }

            override fun getValue(buffer: ByteBuffer) = RgbaColor(
                r = buffer.getFloat(offset + 0),
                g = buffer.getFloat(offset + 4),
                b = buffer.getFloat(offset + 8),
                a = buffer.getFloat(offset + 12),
            )
        }

        class IVec2Field(layout: GpuDataLayout<*>) : Field<Vector2ic>(
            layout = layout,
            align = layout.strategy.vec2Align,
            size = layout.strategy.vec2Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector2ic) {
                value.get(offset, buffer)
            }

            private val vector = Vector2i()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class IVec3Field(layout: GpuDataLayout<*>) : Field<Vector3ic>(
            layout = layout,
            align = layout.strategy.vec3Align,
            size = layout.strategy.vec3Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector3ic) {
                value.get(offset, buffer)
            }

            private val vector = Vector3i()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class IVec4Field(layout: GpuDataLayout<*>) : Field<Vector4ic>(
            layout = layout,
            align = layout.strategy.vec4Align,
            size = layout.strategy.vec4Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Vector4ic) {
                value.get(offset, buffer)
            }

            private val vector = Vector4i()

            override fun getValue(buffer: ByteBuffer) = vector.apply {
                set(offset, buffer)
            }
        }

        class Mat2Field(layout: GpuDataLayout<*>) : Field<Matrix2fc>(
            layout = layout,
            align = layout.strategy.mat2Align,
            size = layout.strategy.mat2Size,
        ) {
            private val padding = layout.strategy.mat2Padding

            override fun setValue(buffer: ByteBuffer, value: Matrix2fc) {
                if (padding) {
                    buffer.putFloat(offset + 0, value.m00())
                    buffer.putFloat(offset + 4, value.m01())
                    buffer.putFloat(offset + 16, value.m10())
                    buffer.putFloat(offset + 20, value.m11())
                } else {
                    value.get(offset, buffer)
                }
            }

            private val matrix = Matrix2f()

            override fun getValue(buffer: ByteBuffer) = matrix.apply {
                if (padding) {
                    m00(buffer.getFloat(offset + 0))
                    m01(buffer.getFloat(offset + 4))
                    m10(buffer.getFloat(offset + 16))
                    m11(buffer.getFloat(offset + 20))
                } else {
                    set(offset, buffer)
                }
            }
        }

        class Mat3Field(layout: GpuDataLayout<*>) : Field<Matrix3fc>(
            layout = layout,
            align = layout.strategy.mat3Align,
            size = layout.strategy.mat3Size,
        ) {
            private val padding = layout.strategy.mat3Padding

            override fun setValue(buffer: ByteBuffer, value: Matrix3fc) {
                if (padding) {
                    buffer.putFloat(offset + 0, value.m00())
                    buffer.putFloat(offset + 4, value.m01())
                    buffer.putFloat(offset + 8, value.m02())
                    buffer.putFloat(offset + 16, value.m10())
                    buffer.putFloat(offset + 20, value.m11())
                    buffer.putFloat(offset + 24, value.m12())
                    buffer.putFloat(offset + 32, value.m20())
                    buffer.putFloat(offset + 36, value.m21())
                    buffer.putFloat(offset + 40, value.m22())
                } else {
                    value.get(offset, buffer)
                }
            }

            private val matrix = Matrix3f()

            override fun getValue(buffer: ByteBuffer) = matrix.apply {
                if (padding) {
                    m00(buffer.getFloat(offset + 0))
                    m01(buffer.getFloat(offset + 4))
                    m02(buffer.getFloat(offset + 8))
                    m10(buffer.getFloat(offset + 16))
                    m11(buffer.getFloat(offset + 20))
                    m12(buffer.getFloat(offset + 24))
                    m20(buffer.getFloat(offset + 32))
                    m21(buffer.getFloat(offset + 36))
                    m22(buffer.getFloat(offset + 40))
                } else {
                    set(offset, buffer)
                }
            }
        }

        class Mat4Field(layout: GpuDataLayout<*>) : Field<Matrix4fc>(
            layout = layout,
            align = layout.strategy.mat4Align,
            size = layout.strategy.mat4Size,
        ) {
            override fun setValue(buffer: ByteBuffer, value: Matrix4fc) {
                value.get(offset, buffer)
            }

            private val matrix = Matrix4f()

            override fun getValue(buffer: ByteBuffer) = matrix.apply {
                set(offset, buffer)
            }
        }

        abstract class ArrayField<T>(
            layout: GpuDataLayout<*>,
            length: Int,
            baseAlign: Int,
            baseSize: Int,
        ) : Field<T>(
            layout = layout,
            align = layout.strategy.arrayAlignmentOf(
                baseAlign = baseAlign,
                baseSize = baseSize,
            ),
            size = layout.strategy.arrayStrideOf(
                baseAlign = baseAlign,
                baseSize = baseSize,
            ) * length,
        ) {
            protected val stride = layout.strategy.arrayStrideOf(
                baseAlign = baseAlign,
                baseSize = baseSize,
            )
        }

        class IntArray(layout: GpuDataLayout<*>, private val length: Int) : ArrayField<IntList>(
            layout = layout,
            baseAlign = layout.strategy.scalarAlign,
            baseSize = layout.strategy.scalarSize,
            length = length,
        ) {
            override fun setValue(buffer: ByteBuffer, value: IntList) {
                require(value.size == length) { "List size mismatch: expected $length, but got ${value.size}" }
                repeat(length) { index ->
                    val item = value.getInt(index)
                    buffer.putInt(offset + index * stride, item)
                }
            }

            private val list = object : AbstractIntList() {
                var buffer: ByteBuffer? = null

                override fun add(key: Int?) = throw UnsupportedOperationException()
                override fun remove(key: Int?) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun getInt(index: Int): Int {
                    checkIndex(index, length)
                    return requireNotNull(buffer).getInt(offset + index * stride)
                }

                override fun set(index: Int, k: Int): Int = getInt(index).also {
                    requireNotNull(buffer).putInt(offset + index * stride, k)
                }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class FloatArray(layout: GpuDataLayout<*>, private val length: Int) : ArrayField<FloatList>(
            layout = layout,
            baseAlign = layout.strategy.scalarAlign,
            baseSize = layout.strategy.scalarSize,
            length = length,
        ) {
            override fun setValue(buffer: ByteBuffer, value: FloatList) {
                require(value.size == length) { "List size mismatch: expected $length, but got ${value.size}" }
                repeat(length) { index ->
                    val item = value.getFloat(index)
                    buffer.putFloat(offset + index * stride, item)
                }
            }

            private val list = object : AbstractFloatList() {
                var buffer: ByteBuffer? = null

                override fun add(key: Float?) = throw UnsupportedOperationException()
                override fun remove(key: Float?) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun getFloat(index: Int): Float {
                    checkIndex(index, length)
                    return requireNotNull(buffer).getFloat(offset + index * stride)
                }

                override fun set(index: Int, k: Float): Float = getFloat(index).also {
                    requireNotNull(buffer).putFloat(offset + index * stride, k)
                }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class Vec2Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector2fc>>(
            layout = layout,
            baseAlign = layout.strategy.vec2Align,
            baseSize = layout.strategy.vec2Size,
            length = length,
        ) {
            private val vector = Vector2f()
            private val list = object : AbstractMutableList<Vector2fc>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector2fc) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector2fc {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector2fc) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector2fc>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class Vec3Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector3fc>>(
            layout = layout,
            baseAlign = layout.strategy.vec3Align,
            baseSize = layout.strategy.vec3Size,
            length = length,
        ) {
            private val vector = Vector3f()
            private val list = object : AbstractMutableList<Vector3fc>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector3fc) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector3fc {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector3fc) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector3fc>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class Vec4Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector4fc>>(
            layout = layout,
            baseAlign = layout.strategy.vec4Align,
            baseSize = layout.strategy.vec4Size,
            length = length,
        ) {
            private val vector = Vector4f()
            private val list = object : AbstractMutableList<Vector4fc>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector4fc) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector4fc {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector4fc) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector4fc>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class IVec2Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector2ic>>(
            layout = layout,
            baseAlign = layout.strategy.vec2Align,
            baseSize = layout.strategy.vec2Size,
            length = length,
        ) {
            private val vector = Vector2i()
            private val list = object : AbstractMutableList<Vector2ic>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector2ic) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector2ic {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector2ic) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector2ic>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class IVec3Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector3ic>>(
            layout = layout,
            baseAlign = layout.strategy.vec3Align,
            baseSize = layout.strategy.vec3Size,
            length = length,
        ) {
            private val vector = Vector3i()
            private val list = object : AbstractMutableList<Vector3ic>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector3ic) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector3ic {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector3ic) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector3ic>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class IVec4Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Vector4ic>>(
            layout = layout,
            baseAlign = layout.strategy.vec4Align,
            baseSize = layout.strategy.vec4Size,
            length = length,
        ) {
            private val vector = Vector4i()
            private val list = object : AbstractMutableList<Vector4ic>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Vector4ic) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Vector4ic {
                    checkIndex(index, length)
                    return vector.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Vector4ic) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Vector4ic>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }

        class Mat4Array(
            layout: GpuDataLayout<*>,
            private val length: Int,
        ) : ArrayField<MutableList<Matrix4fc>>(
            layout = layout,
            baseAlign = layout.strategy.mat4Align,
            baseSize = layout.strategy.mat4Size,
            length = length,
        ) {
            private val matrix = Matrix4f()
            private val list = object : AbstractMutableList<Matrix4fc>() {
                var buffer: ByteBuffer? = null

                override fun add(index: Int, element: Matrix4fc) = throw UnsupportedOperationException()
                override fun removeAt(index: Int) = throw UnsupportedOperationException()

                override val size: Int
                    get() = length

                override fun get(index: Int): Matrix4fc {
                    checkIndex(index, length)
                    return matrix.apply { set(offset + index * stride, buffer) }
                }

                override fun set(index: Int, element: Matrix4fc) = get(index).also {
                    element.get(offset + index * stride, requireNotNull(buffer))
                }
            }

            override fun setValue(buffer: ByteBuffer, value: MutableList<Matrix4fc>) {
                require(value.size == length) { "List size mismatch" }
                value.forEachIndexed { i, vec -> vec.get(offset + i * stride, buffer) }
            }

            override fun getValue(buffer: ByteBuffer) = list.also { it.buffer = buffer }
        }
    }

    fun int() = Field.IntField(this)
    fun uint() = Field.UIntField(this)
    fun float() = Field.FloatField(this)

    fun vec2() = Field.Vec2Field(this)
    fun vec3() = Field.Vec3Field(this)
    fun vec4() = Field.Vec4Field(this)
    fun rgba() = Field.RgbaColorField(this)

    fun ivec2() = Field.IVec2Field(this)
    fun ivec3() = Field.IVec3Field(this)
    fun ivec4() = Field.IVec4Field(this)

    fun mat2() = Field.Mat2Field(this)
    fun mat3() = Field.Mat3Field(this)
    fun mat4() = Field.Mat4Field(this)

    fun intArray(size: Int) = Field.IntArray(this, size)
    fun floatArray(size: Int) = Field.FloatArray(this, size)

    fun vec2Array(size: Int) = Field.Vec2Array(this, size)
    fun vec3Array(size: Int) = Field.Vec3Array(this, size)
    fun vec4Array(size: Int) = Field.Vec4Array(this, size)

    fun ivec2Array(size: Int) = Field.IVec2Array(this, size)
    fun ivec3Array(size: Int) = Field.IVec3Array(this, size)
    fun ivec4Array(size: Int) = Field.IVec4Array(this, size)

    fun mat4Array(size: Int) = Field.Mat4Array(this, size)

    fun withBuffer(buffer: ByteBuffer, block: L.() -> Unit) {
        try {
            this.buffer = buffer
            @Suppress("UNCHECKED_CAST")
            block.invoke(this as L)
        } finally {
            this.buffer = null
        }
    }
}
