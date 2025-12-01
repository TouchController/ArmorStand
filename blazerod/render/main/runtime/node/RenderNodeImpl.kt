package top.fifthlight.blazerod.runtime.node

import top.fifthlight.blazerod.api.refcount.AbstractRefCount
import top.fifthlight.blazerod.api.resource.RenderNode
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.NodeId
import top.fifthlight.blazerod.model.NodeTransformView
import top.fifthlight.blazerod.model.TransformId
import top.fifthlight.blazerod.runtime.ModelInstanceImpl
import top.fifthlight.blazerod.runtime.node.component.RenderNodeComponent

class RenderNodeImpl(
    override val nodeIndex: Int,
    override val absoluteTransform: NodeTransformView?,
    val components: List<RenderNodeComponent<*>>,
    // Just for animation
    override val nodeId: NodeId? = null,
    override val nodeName: String? = null,
    override val humanoidTags: List<HumanoidTag> = listOf(),
) : AbstractRefCount(), RenderNode {
    // Defined as lateinit to avoid cyclic dependency
    private var _children: List<RenderNodeImpl>? = null
    val children: List<RenderNodeImpl>
        get() = _children ?: error("Children not initialized")

    fun initializeChildren(children: List<RenderNodeImpl>) {
        require(_children == null) { "Children already initialized" }
        _children = children
        for (child in children) {
            child.increaseReferenceCount()
            child.parent = this
        }
    }

    override val typeId: String
        get() = "node"

    var parent: RenderNodeImpl? = null
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

    fun update(phase: UpdatePhase, node: RenderNodeImpl, instance: ModelInstanceImpl) {
        if (phase == UpdatePhase.GlobalTransformPropagation) {
            if (!instance.isNodeTransformDirty(node)) {
                return
            }
            val parent = parent
            val transformMap = instance.getTransformMap(this)
            val worldTransform = instance.getWorldTransform(this)
            val currentLocalTransform = transformMap.getSum(TransformId.LAST)
            if (parent != null) {
                instance.getWorldTransform(parent).mul(currentLocalTransform, worldTransform)
            } else {
                worldTransform.set(currentLocalTransform)
            }
            instance.cleanNodeTransformDirty(node)
        } else {
            phaseComponents[phase.type]?.forEach { component ->
                component.update(phase, node, instance)
            }
        }
    }
}

fun RenderNodeImpl.forEach(action: (RenderNodeImpl) -> Unit) {
    val queue = ArrayDeque<RenderNodeImpl>()
    queue.add(this)

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        action(node)
        queue.addAll(node.children)
    }
}

fun ModelInstanceImpl.getTransformMap(node: RenderNodeImpl) = modelData.transformMaps[node.nodeIndex]
fun ModelInstanceImpl.getWorldTransform(node: RenderNodeImpl) = modelData.worldTransforms[node.nodeIndex]
fun ModelInstanceImpl.getTransformMap(nodeIndex: Int) = modelData.transformMaps[nodeIndex]
fun ModelInstanceImpl.getWorldTransform(nodeIndex: Int) = modelData.worldTransforms[nodeIndex]
fun ModelInstanceImpl.getWorldTransformNoPhysics(node: RenderNodeImpl) = modelData.worldTransformsNoPhysics[node.nodeIndex]
fun ModelInstanceImpl.getWorldTransformNoPhysics(nodeIndex: Int) = modelData.worldTransformsNoPhysics[nodeIndex]
private fun ModelInstanceImpl.isNodeTransformDirty(node: RenderNodeImpl) = modelData.transformDirty[node.nodeIndex]
fun ModelInstanceImpl.markNodeTransformDirty(node: RenderNodeImpl) {
    if (!modelData.transformDirty[node.nodeIndex]) {
        modelData.transformDirty[node.nodeIndex] = true
        modelData.undirtyNodeCount--
        for (children in node.children) {
            markNodeTransformDirty(children)
        }
    }
}

private fun ModelInstanceImpl.cleanNodeTransformDirty(node: RenderNodeImpl) {
    if (modelData.transformDirty[node.nodeIndex]) {
        modelData.transformDirty[node.nodeIndex] = false
        modelData.undirtyNodeCount++
    }
}

