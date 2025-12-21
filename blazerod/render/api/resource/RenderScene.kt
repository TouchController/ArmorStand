package top.fifthlight.blazerod.api.resource

import top.fifthlight.blazerod.api.refcount.RefCount
import top.fifthlight.blazerod.model.Camera
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.NodeId
import top.fifthlight.blazerod.model.NodeTransformView

interface RenderScene : RefCount {
    val rootNode: RenderNode
    val nodes: List<RenderNode>
    val expressions: List<RenderExpression>
    val expressionGroups: List<RenderExpressionGroup>
    val cameras: List<Camera>

    val renderTransform: NodeTransformView?

    val ikTargetData: List<IkTargetData>
    val nodeIdMap: Map<NodeId, RenderNode>
    val nodeNameMap: Map<String, RenderNode>
    val humanoidTagMap: Map<HumanoidTag, RenderNode>

    data class IkTargetData(val effectorNode: RenderNode)
}