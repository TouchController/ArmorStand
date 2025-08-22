package top.fifthlight.blazerod.model.node

import net.minecraft.util.Identifier
import org.joml.Matrix4fc
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.model.node.component.RenderNodeComponent
import top.fifthlight.blazerod.util.AbstractRefCount

class RenderNode(
    val nodeIndex: Int,
    val absoluteTransform: NodeTransform?,
    val components: List<RenderNodeComponent<*>>,
    // Just for animation
    val nodeId: NodeId? = null,
    val nodeName: String? = null,
    val humanoidTags: List<HumanoidTag> = listOf(),
) : AbstractRefCount() {
    // Defined as lateinit to avoid cyclic dependency
    private var _children: List<RenderNode>? = null
    val children: List<RenderNode>
        get() = _children ?: error("Children not initialized")

    fun initializeChildren(children: List<RenderNode>) {
        require(_children == null) { "Children already initialized" }
        _children = children
        for (child in children) {
            child.increaseReferenceCount()
            child.parent = this
        }
    }

    companion object {
        private val TYPE_ID = Identifier.of("blazerod", "node")
    }

    override val typeId: Identifier
        get() = TYPE_ID

    var parent: RenderNode? = null
        private set

    init {
        for (component in components) {
            component.increaseReferenceCount()
            component.node = this
        }
    }

    override fun onClosed() {
        for (component in components) {
            component.decreaseReferenceCount()
        }
        for (child in children) {
            child.decreaseReferenceCount()
            child.parent = null
        }
    }

    private val typeComponents = components.groupBy { it.type }
    private val phaseComponents =
        UpdatePhase.Type.entries.associateWith { type -> components.filter { type in it.updatePhases } }
    private val phases = components.flatMap { it.updatePhases }.toSet()
    fun hasPhase(phase: UpdatePhase.Type) = phase in phases
    @Suppress("UNCHECKED_CAST")
    fun <T : RenderNodeComponent<T>> getComponentsOfType(type: RenderNodeComponent.Type<T>): List<T> =
        typeComponents[type] as? List<T> ?: listOf()
    fun hasComponentOfType(type: RenderNodeComponent.Type<*>): Boolean = type in typeComponents.keys

    fun update(phase: UpdatePhase, instance: ModelInstance) {
        if (phase == UpdatePhase.GlobalTransformPropagation) {
            if (!instance.isNodeTransformDirty(this)) {
                return
            }
            val parent = parent
            val transformMap = instance.getTransformMap(this)
            val worldTransform = instance.modelData.worldTransforms[nodeIndex]
            val currentLocalTransform = transformMap.getSum(TransformId.LAST)
            if (parent != null) {
                instance.getWorldTransform(parent).mul(currentLocalTransform, worldTransform)
            } else {
                worldTransform.set(currentLocalTransform)
            }
            instance.cleanNodeTransformDirty(this)
        } else {
            phaseComponents[phase.type]?.forEach { component ->
                component.update(phase, this, instance)
            }
        }
    }
}

fun RenderNode.forEach(action: (RenderNode) -> Unit) {
    val queue = ArrayDeque<RenderNode>()
    queue.add(this)

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        action(node)
        queue.addAll(node.children)
    }
}

fun ModelInstance.getTransformMap(node: RenderNode) = modelData.transformMaps[node.nodeIndex]
fun ModelInstance.getWorldTransform(node: RenderNode): Matrix4fc = modelData.worldTransforms[node.nodeIndex]
fun ModelInstance.getTransformMap(nodeIndex: Int) = modelData.transformMaps[nodeIndex]
fun ModelInstance.getWorldTransform(nodeIndex: Int): Matrix4fc = modelData.worldTransforms[nodeIndex]
private fun ModelInstance.isNodeTransformDirty(node: RenderNode) = modelData.transformDirty[node.nodeIndex]
fun ModelInstance.markNodeTransformDirty(node: RenderNode) {
    if (!modelData.transformDirty[node.nodeIndex]) {
        modelData.transformDirty[node.nodeIndex] = true
        modelData.undirtyNodeCount--
        for (children in node.children) {
            markNodeTransformDirty(children)
        }
    }
}

private fun ModelInstance.cleanNodeTransformDirty(node: RenderNode) {
    if (modelData.transformDirty[node.nodeIndex]) {
        modelData.transformDirty[node.nodeIndex] = false
        modelData.undirtyNodeCount++
    }
}

