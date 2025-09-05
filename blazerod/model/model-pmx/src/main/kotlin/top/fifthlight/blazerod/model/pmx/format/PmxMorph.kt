package top.fifthlight.blazerod.model.pmx.format

import top.fifthlight.blazerod.model.Expression
import top.fifthlight.blazerod.model.Primitive

internal data class PmxMorph(
    val pmxIndex: Int,
    val targetIndex: Int,
    val nameLocal: String? = null,
    val nameUniversal: String? = null,
    val tag: Expression.Tag? = null,
    val data: Map<Int, Primitive.Attributes.MorphTarget>,
)

internal enum class PmxMorphPanelType(val value: Int) {
    HIDDEN(0),
    EYEBROWS(1),
    EYES(2),
    MOUTH(3),
    OTHER(4),
}

internal enum class PmxMorphType(val value: Int) {
    GROUP(0),
    VERTEX(1),
    BONE(2),
    UV(3),
    UV_EXT1(4),
    UV_EXT2(5),
    UV_EXT3(6),
    UV_EXT4(7),
    MATERIAL(8),
    FLIP(9),
    IMPULSE(10),
}

internal data class PmxMorphGroup(
    val nameLocal: String? = null,
    val nameUniversal: String? = null,
    val tag: Expression.Tag? = null,
    val items: List<MorphItem>,
) {
    data class MorphItem(
        val index: Int,
        val influence: Float,
    )
}
