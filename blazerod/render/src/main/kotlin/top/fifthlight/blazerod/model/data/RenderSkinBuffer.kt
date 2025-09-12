package top.fifthlight.blazerod.model.data

import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.joml.Matrix4fc
import top.fifthlight.blazerod.model.resource.RenderSkin
import top.fifthlight.blazerod.util.AbstractRefCount
import top.fifthlight.blazerod.util.CowBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RenderSkinBuffer private constructor(
    val jointSize: Int,
) : CowBuffer.Content<RenderSkinBuffer>,
    AbstractRefCount() {
    companion object {
        private val IDENTITY = Matrix4f()
        const val MAT4X4_SIZE = 4 * 4 * 4
        private val TYPE_ID = Identifier.of("blazerod", "render_skin_buffer")
    }

    override val typeId: Identifier
        get() = TYPE_ID

    constructor(skin: RenderSkin) : this(skin.jointSize)

    val buffer: ByteBuffer = ByteBuffer.allocateDirect(jointSize * 2 * MAT4X4_SIZE).order(ByteOrder.nativeOrder())

    fun clear() {
        repeat(jointSize * 2) {
            IDENTITY.get(it * MAT4X4_SIZE, buffer)
        }
        buffer.rewind()
    }

    private val normalMatrix = Matrix4f()
    fun setMatrix(index: Int, src: Matrix4fc) {
        val basePos = index * 2 * MAT4X4_SIZE
        src.get(basePos, buffer)
        src.normal(normalMatrix)
        normalMatrix.get(basePos + MAT4X4_SIZE, buffer)
    }

    fun getPositionMatrix(index: Int, dest: Matrix4f) {
        dest.set(index * 2 * MAT4X4_SIZE, buffer)
    }

    fun getNormalMatrix(index: Int, dest: Matrix4f) {
        dest.set(index * 2 * MAT4X4_SIZE + MAT4X4_SIZE, buffer)
    }

    override fun copy(): RenderSkinBuffer = RenderSkinBuffer(jointSize).also {
        it.buffer.clear()
        buffer.clear()
        it.buffer.put(buffer)
        it.buffer.clear()
    }

    override fun onClosed() = Unit
}