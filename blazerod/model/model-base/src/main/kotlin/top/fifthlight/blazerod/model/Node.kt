package top.fifthlight.blazerod.model

import java.util.*

data class NodeId(
    val modelId: UUID,
    val index: Int,
)

data class Node(
    val name: String? = null,
    val id: NodeId,
    val transform: NodeTransform? = null,
    val children: List<Node> = listOf(),
    val components: List<NodeComponent> = listOf(),
) {
    val meshIdToSkinMap: Map<MeshId, NodeComponent.SkinComponent>

    init {
        val meshIdToSkinMap = mutableMapOf<MeshId, NodeComponent.SkinComponent>()

        var requireMesh = false
        var typeComponents = mutableMapOf<NodeComponent.Type, MutableList<NodeComponent>>()
        for (component in components) {
            if (component.type.singleInstanceOnly && typeComponents.containsKey(component.type)) {
                throw IllegalArgumentException("Node ${id.index} has multiple components of type ${component.type}")
            }
            requireMesh = requireMesh || component.type.requireMesh
            typeComponents.getOrPut(component.type) { mutableListOf() }.add(component)

            when (component) {
                is NodeComponent.SkinComponent -> {
                    for (meshId in component.meshIds) {
                        if (meshIdToSkinMap.containsKey(meshId)) {
                            throw IllegalArgumentException("Node ${id.index} has multiple skin components for mesh ${meshId.index}")
                        }
                        meshIdToSkinMap[meshId] = component
                    }
                }

                else -> {}
            }
        }
        if (requireMesh && !typeComponents.containsKey(NodeComponent.Type.MESH)) {
            throw IllegalArgumentException("This node must have a mesh component, as it has component requires a mesh")
        }

        this.meshIdToSkinMap = meshIdToSkinMap
    }
}

fun Node.forEach(action: (Node) -> Unit) {
    action(this)
    for (child in children) {
        child.forEach(action)
    }
}
