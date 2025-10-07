package top.fifthlight.blazerod.runtime

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.client.render.VertexConsumerProvider
import org.joml.Matrix4fc
import top.fifthlight.blazerod.api.refcount.AbstractRefCount
import top.fifthlight.blazerod.api.resource.RenderExpression
import top.fifthlight.blazerod.api.resource.RenderExpressionGroup
import top.fifthlight.blazerod.api.resource.RenderScene
import top.fifthlight.blazerod.model.Camera
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.NodeId
import top.fifthlight.blazerod.model.NodeTransform
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import top.fifthlight.blazerod.runtime.node.component.IkTargetComponent
import top.fifthlight.blazerod.runtime.node.component.PrimitiveComponent
import top.fifthlight.blazerod.runtime.node.component.RenderNodeComponent
import top.fifthlight.blazerod.runtime.node.component.RigidBodyComponent
import top.fifthlight.blazerod.runtime.node.forEach
import top.fifthlight.blazerod.runtime.resource.RenderPhysicsJoint
import top.fifthlight.blazerod.runtime.resource.RenderSkin

class RenderSceneImpl(
    override val rootNode: RenderNodeImpl,
    override val nodes: List<RenderNodeImpl>,
    val skins: List<RenderSkin>,
    override val expressions: List<RenderExpression>,
    override val expressionGroups: List<RenderExpressionGroup>,
    override val cameras: List<Camera>,
    val physicsJoints: List<RenderPhysicsJoint>,
    val renderTransform: NodeTransform?,
) : AbstractRefCount(), RenderScene {
    companion object {
        private const val PHYSICS_MAX_SUB_STEP_COUNT = 10
    }
    override val typeId: String
        get() = "scene"

    private val sortedNodes: List<RenderNodeImpl>
    private val debugRenderNodes: List<RenderNodeImpl>
    val primitiveComponents: List<PrimitiveComponent>
    val morphedPrimitiveComponents: List<PrimitiveComponent>
    override val ikTargetData: List<RenderScene.IkTargetData>
    val ikTargetComponents: List<IkTargetComponent>
    val rigidBodyComponents: List<RigidBodyComponent>
    override val nodeIdMap: Map<NodeId, RenderNodeImpl>
    override val nodeNameMap: Map<String, RenderNodeImpl>
    override val humanoidTagMap: Map<HumanoidTag, RenderNodeImpl>
    val hasPhysics: Boolean

    init {
        rootNode.increaseReferenceCount()
        val nodes = mutableListOf<RenderNodeImpl>()
        val debugRenderNodes = mutableListOf<RenderNodeImpl>()
        val primitiveComponents = mutableListOf<PrimitiveComponent>()
        val morphedPrimitives = Int2ReferenceOpenHashMap<PrimitiveComponent>()
        val ikTargets = Int2ReferenceOpenHashMap<IkTargetComponent>()
        val rigidBodyComponents = Int2ReferenceOpenHashMap<RigidBodyComponent>()
        val nodeIdMap = mutableMapOf<NodeId, RenderNodeImpl>()
        val nodeNameMap = mutableMapOf<String, RenderNodeImpl>()
        val humanoidTagMap = mutableMapOf<HumanoidTag, RenderNodeImpl>()
        var hasPhysics = false
        rootNode.forEach { node ->
            nodes.add(node)
            node.nodeId?.let { nodeIdMap.put(it, node) }
            node.nodeName?.let { nodeNameMap.put(it, node) }
            node.humanoidTags.forEach { humanoidTagMap[it] = node }
            if (node.hasPhase(UpdatePhase.Type.DEBUG_RENDER)) {
                debugRenderNodes.add(node)
            }
            node.getComponentsOfType(RenderNodeComponent.Type.Primitive).let { components ->
                primitiveComponents.addAll(components)
                for (component in components) {
                    component.morphedPrimitiveIndex?.let { index ->
                        if (morphedPrimitives.containsKey(index)) {
                            throw IllegalStateException("Duplicate morphed primitive index: $index")
                        }
                        morphedPrimitives.put(index, component)
                    }
                }
            }
            node.getComponentsOfType(RenderNodeComponent.Type.IkTarget).forEach { component ->
                ikTargets.put(component.ikIndex, component)
            }
            node.getComponentsOfType(RenderNodeComponent.Type.RigidBody).forEach { component ->
                hasPhysics = true
                rigidBodyComponents.put(component.rigidBodyIndex, component)
            }
        }
        this.sortedNodes = nodes
        this.debugRenderNodes = debugRenderNodes
        this.primitiveComponents = primitiveComponents
        this.morphedPrimitiveComponents = (0 until morphedPrimitives.size).map {
            morphedPrimitives.get(it) ?: error("Morphed primitive index not found: $it")
        }
        this.ikTargetData = (0 until ikTargets.size).map {
            val ikTarget = ikTargets.get(it) ?: error("Ik target index not found: $it")
            RenderScene.IkTargetData(nodes[ikTarget.effectorNodeIndex])
        }
        this.ikTargetComponents = (0 until ikTargets.size).map {
            ikTargets.get(it) ?: error("Ik target index not found: $it")
        }
        this.rigidBodyComponents = (0 until rigidBodyComponents.size).map {
            rigidBodyComponents.get(it) ?: error("Rigid body index not found: $it")
        }
        this.nodeIdMap = nodeIdMap
        this.nodeNameMap = nodeNameMap
        this.humanoidTagMap = humanoidTagMap
        this.hasPhysics = hasPhysics
    }

    private fun executePhase(instance: ModelInstanceImpl, phase: UpdatePhase) {
        for (node in sortedNodes) {
            node.update(phase, node, instance)
        }
    }

    fun updateCamera(instance: ModelInstanceImpl) {
        if (cameras.isEmpty()) {
            return
        }
        if (instance.modelData.undirtyNodeCount == nodes.size) {
            return
        }
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.IkUpdate)
        executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.CameraUpdate)
    }

    fun debugRender(instance: ModelInstanceImpl, viewProjectionMatrix: Matrix4fc, consumers: VertexConsumerProvider) {
        if (debugRenderNodes.isEmpty()) {
            return
        }
        if (instance.modelData.undirtyNodeCount != nodes.size) {
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
            executePhase(instance, UpdatePhase.IkUpdate)
            executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        }
        UpdatePhase.DebugRender.acquire(viewProjectionMatrix, consumers).use {
            executePhase(instance, it)
        }
    }

    fun updateRenderData(instance: ModelInstanceImpl) {
        if (instance.modelData.undirtyNodeCount == nodes.size) {
            return
        }
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.IkUpdate)
        executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.RenderDataUpdate)
    }

    internal fun attachToInstance(instance: ModelInstanceImpl) {
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.IkUpdate)
        executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        for (node in nodes) {
            for (component in node.components) {
                component.onAttached(instance, node)
            }
        }
    }

    override fun onClosed() {
        rootNode.decreaseReferenceCount()
    }
}
