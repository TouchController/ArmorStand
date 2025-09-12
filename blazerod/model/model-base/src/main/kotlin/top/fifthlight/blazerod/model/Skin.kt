package top.fifthlight.blazerod.model

import org.joml.Matrix4fc

data class Skin(
    val name: String? = null,
    val joints: List<NodeId>,
    val inverseBindMatrices: List<Matrix4fc>? = null,
    val skeleton: NodeId? = null,
    val jointHumanoidTags: List<HumanoidTag?>? = null,
)
