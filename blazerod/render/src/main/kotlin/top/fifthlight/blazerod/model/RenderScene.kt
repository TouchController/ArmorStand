package top.fifthlight.blazerod.model

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.util.Identifier
import org.joml.Matrix4fc
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.UpdatePhase
import top.fifthlight.blazerod.model.node.component.IkTargetNodeComponent
import top.fifthlight.blazerod.model.node.component.PrimitiveNodeComponent
import top.fifthlight.blazerod.model.node.component.RenderNodeComponent
import top.fifthlight.blazerod.model.node.component.RigidBodyNodeComponent
import top.fifthlight.blazerod.model.node.forEach
import top.fifthlight.blazerod.model.resource.*
import top.fifthlight.blazerod.util.AbstractRefCount

class RenderScene(
    val rootNode: RenderNode,
    val nodes: List<RenderNode>,
    val skins: List<RenderSkin>,
    val expressions: List<RenderExpression>,
    val expressionGroups: List<RenderExpressionGroup>,
    val cameras: List<RenderCamera>,
    val physicsJoints: List<RenderPhysicsJoint>,
) : AbstractRefCount() {
    companion object {
        private val TYPE_ID = Identifier.of("blazerod", "scene")
        private const val PHYSICS_MAX_SUB_STEP_COUNT = 10
    }

    override val typeId: Identifier
        get() = TYPE_ID

    private val sortedNodes: List<RenderNode>
    private val debugRenderNodes: List<RenderNode>
    val primitiveComponents: List<PrimitiveNodeComponent>
    val morphedPrimitiveComponents: List<PrimitiveNodeComponent>
    val ikTargetComponents: List<IkTargetNodeComponent>
    val rigidBodyComponents: List<RigidBodyNodeComponent>
    val nodeIdMap: Map<NodeId, RenderNode>
    val nodeNameMap: Map<String, RenderNode>
    val humanoidTagMap: Map<HumanoidTag, RenderNode>
    val hasPhysics: Boolean
    init {
        rootNode.increaseReferenceCount()
        val nodes = mutableListOf<RenderNode>()
        val debugRenderNodes = mutableListOf<RenderNode>()
        val primitiveComponents = mutableListOf<PrimitiveNodeComponent>()
        val morphedPrimitives = Int2ReferenceOpenHashMap<PrimitiveNodeComponent>()
        val ikTargets = Int2ReferenceOpenHashMap<IkTargetNodeComponent>()
        val rigidBodyComponents = Int2ReferenceOpenHashMap<RigidBodyNodeComponent>()
        val nodeIdMap = mutableMapOf<NodeId, RenderNode>()
        val nodeNameMap = mutableMapOf<String, RenderNode>()
        val humanoidTagMap = mutableMapOf<HumanoidTag, RenderNode>()
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

    private fun executePhase(instance: ModelInstance, phase: UpdatePhase) {
        for (node in sortedNodes) {
            node.update(phase, instance)
        }
    }

    fun updateCamera(instance: ModelInstance) {
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

    fun debugRender(
        instance: ModelInstance,
        viewProjectionMatrix: Matrix4fc,
        consumers: VertexConsumerProvider,
        time: Float, // For physics, in seconds
    ) {
        if (debugRenderNodes.isEmpty()) {
            return
        }
        if (instance.modelData.undirtyNodeCount != nodes.size) {
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
            executePhase(instance, UpdatePhase.IkUpdate)
            executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        }
        instance.physicsData?.let { data ->
            if (data.lastPhysicsTime < 0) {
                data.lastPhysicsTime = time
                return@let
            }
            val timeStep = time - data.lastPhysicsTime
            data.lastPhysicsTime = time
            executePhase(instance, UpdatePhase.PhysicsUpdatePre)
            data.world.update(timeStep, PHYSICS_MAX_SUB_STEP_COUNT)
            executePhase(instance, UpdatePhase.PhysicsUpdatePost)
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        }
        UpdatePhase.DebugRender.acquire(viewProjectionMatrix, consumers).use {
            executePhase(instance, it)
        }
    }

    fun updateRenderData(
        instance: ModelInstance,
        time: Float, // For physics, in seconds
    ) {
        if (instance.modelData.undirtyNodeCount == nodes.size) {
            return
        }
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.IkUpdate)
        executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        instance.physicsData?.let { data ->
            if (data.lastPhysicsTime < 0) {
                data.lastPhysicsTime = time
                return@let
            }
            val timeStep = time - data.lastPhysicsTime
            data.lastPhysicsTime = time
            executePhase(instance, UpdatePhase.PhysicsUpdatePre)
            data.world.update(timeStep, PHYSICS_MAX_SUB_STEP_COUNT)
            executePhase(instance, UpdatePhase.PhysicsUpdatePost)
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        }
        executePhase(instance, UpdatePhase.RenderDataUpdate)
    }

    internal fun attachToInstance(instance: ModelInstance) {
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
