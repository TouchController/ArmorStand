package top.fifthlight.blazerod.runtime

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.client.render.VertexConsumerProvider
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.blazerod.api.refcount.AbstractRefCount
import top.fifthlight.blazerod.api.resource.RenderExpression
import top.fifthlight.blazerod.api.resource.RenderExpressionGroup
import top.fifthlight.blazerod.api.resource.RenderScene
import top.fifthlight.blazerod.model.Camera
import top.fifthlight.blazerod.model.HumanoidTag
import top.fifthlight.blazerod.model.NodeId
import top.fifthlight.blazerod.model.NodeTransform
import top.fifthlight.blazerod.physics.PhysicsInterface
import top.fifthlight.blazerod.physics.PhysicsScene
import top.fifthlight.blazerod.runtime.node.RenderNodeImpl
import top.fifthlight.blazerod.runtime.node.UpdatePhase
import top.fifthlight.blazerod.runtime.node.component.IkTargetComponent
import top.fifthlight.blazerod.runtime.node.component.PrimitiveComponent
import top.fifthlight.blazerod.runtime.node.component.RenderNodeComponent
import top.fifthlight.blazerod.runtime.node.component.RigidBodyComponent
import top.fifthlight.blazerod.runtime.node.forEach
import top.fifthlight.blazerod.runtime.resource.RenderPhysicsJoint
import top.fifthlight.blazerod.runtime.resource.RenderSkin
import kotlin.time.measureTime

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
        private const val PHYSICS_FPS = 120f
        private const val PHYSICS_TIME_STEP = 1f / PHYSICS_FPS
    }

    override val typeId: String
        get() = "scene"

    private val sortedNodes: List<RenderNodeImpl>
    private val debugRenderNodes: List<RenderNodeImpl>
    val primitiveComponents: List<PrimitiveComponent>
    val morphedPrimitiveComponents: List<PrimitiveComponent>
    override val ikTargetData: List<RenderScene.IkTargetData>
    val ikTargetComponents: List<IkTargetComponent>
    val rigidBodyComponents: List<Pair<Int, RigidBodyComponent>>
    override val nodeIdMap: Map<NodeId, RenderNodeImpl>
    override val nodeNameMap: Map<String, RenderNodeImpl>
    override val humanoidTagMap: Map<HumanoidTag, RenderNodeImpl>
    val physicsScene: PhysicsScene?

    init {
        rootNode.increaseReferenceCount()
        val nodes = mutableListOf<RenderNodeImpl>()
        val debugRenderNodes = mutableListOf<RenderNodeImpl>()
        val primitiveComponents = mutableListOf<PrimitiveComponent>()
        val morphedPrimitives = Int2ReferenceOpenHashMap<PrimitiveComponent>()
        val ikTargets = Int2ReferenceOpenHashMap<IkTargetComponent>()
        val rigidBodyComponents = Int2ReferenceOpenHashMap<Pair<Int, RigidBodyComponent>>()
        val nodeIdMap = mutableMapOf<NodeId, RenderNodeImpl>()
        val nodeNameMap = mutableMapOf<String, RenderNodeImpl>()
        val humanoidTagMap = mutableMapOf<HumanoidTag, RenderNodeImpl>()
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
                rigidBodyComponents.put(component.rigidBodyIndex, node.nodeIndex to component)
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
        this.physicsScene = this.rigidBodyComponents.takeIf {
            PhysicsInterface.isPhysicsAvailable && it.isNotEmpty()
        }?.let { components ->
            PhysicsScene(
                rigidBodies = components.map { (nodeIndex, component) -> component.rigidBodyData },
                joints = physicsJoints,
            )
        }
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

    private fun updatePhysics(
        instance: ModelInstanceImpl,
        time: Float, // For physics, in seconds
    ) {
        instance.physicsData?.let { data ->
            if (data.lastPhysicsTime < 0) {
                data.lastPhysicsTime = time
                return@let
            }
            val timeStep = time - data.lastPhysicsTime
            if (timeStep <= 0f) {
                return@let
            }

            data.lastPhysicsTime = time

            instance.updateWorldTransformsNoPhysics()
            executePhase(instance, UpdatePhase.PhysicsUpdatePre)
            data.world.pushTransforms(data.transformArray)
            measureTime {
                data.world.step(timeStep, PHYSICS_MAX_SUB_STEP_COUNT, PHYSICS_TIME_STEP)
            }.let {
                println("Physics step time: $it, timeStep: $timeStep")
            }
            data.world.pullTransforms(data.transformArray)

            run {
                val array = data.transformArray
                val basePos = Vector3f()
                val baseRot = Quaternionf()
                val clampMatrix = Matrix4f()
                val maxDistSq = 4.0f
                val maxRadiusSq = 100.0f

                for (i in 0 until rigidBodyComponents.size) {
                    val (nodeIndex, component) = rigidBodyComponents[i]
                    val offset = component.rigidBodyIndex * 7

                    val px = array[offset + 0]
                    val py = array[offset + 1]
                    val pz = array[offset + 2]

                    val baseWorld = instance.modelData.worldTransformsNoPhysics[nodeIndex]
                    baseWorld.getTranslation(basePos)
                    baseWorld.getUnnormalizedRotation(baseRot)

                    val dx = px - basePos.x
                    val dy = py - basePos.y
                    val dz = pz - basePos.z
                    val distSq = dx * dx + dy * dy + dz * dz
                    val bodyRadiusSq = px * px + py * py + pz * pz

                    if (distSq > maxDistSq || bodyRadiusSq > maxRadiusSq) {
                        array[offset + 0] = basePos.x
                        array[offset + 1] = basePos.y
                        array[offset + 2] = basePos.z
                        array[offset + 3] = baseRot.x
                        array[offset + 4] = baseRot.y
                        array[offset + 5] = baseRot.z
                        array[offset + 6] = baseRot.w

                        clampMatrix.translationRotate(basePos, baseRot)
                        data.world.resetRigidBody(component.rigidBodyIndex, basePos, baseRot)
                    }
                }
            }

            if (data.explosionLogCount < 64) {
                val array = data.transformArray
                val bonePos = Vector3f()
                for (i in 0 until rigidBodyComponents.size) {
                    val (nodeIndex, component) = rigidBodyComponents[i]
                    val offset = component.rigidBodyIndex * 7
                    val px = array[offset + 0]
                    val py = array[offset + 1]
                    val pz = array[offset + 2]

                    val boneWorld = instance.modelData.worldTransforms[nodeIndex]
                    boneWorld.getTranslation(bonePos)

                    val dx = px - bonePos.x
                    val dy = py - bonePos.y
                    val dz = pz - bonePos.z
                    val distSq = dx * dx + dy * dy + dz * dz
                    val bodyRadiusSq = px * px + py * py + pz * pz

                    if (distSq > 9.0f || bodyRadiusSq > 400.0f) {
                        val node = nodes[nodeIndex]
                        val mode = component.rigidBodyData.physicsMode
                        println(
                            "PHYSERR RB_EXPLODE " +
                                "step=${data.debugStepCount} " +
                                "idx=${component.rigidBodyIndex} " +
                                "nodeIndex=$nodeIndex " +
                                "nodeName=${node.nodeName} " +
                                "mode=$mode " +
                                "bodyPos=($px,$py,$pz) " +
                                "bonePos=(${bonePos.x},${bonePos.y},${bonePos.z}) " +
                                "distSq=$distSq " +
                                "bodyRadiusSq=$bodyRadiusSq"
                        )
                        data.explosionLogCount++
                        if (data.explosionLogCount >= 64) {
                            break
                        }
                    }
                }
            }

            if (data.debugStepCount < 10) {
                val maxLogged = minOf(rigidBodyComponents.size, 160)
                for (i in 0 until maxLogged) {
                    val (nodeIndex, component) = rigidBodyComponents[i]
                    val node = nodes[nodeIndex]

                    val boneWorld = instance.modelData.worldTransforms[nodeIndex]
                    val bonePos = Vector3f()
                    val boneRot = Quaternionf()
                    boneWorld.getTranslation(bonePos)
                    boneWorld.getUnnormalizedRotation(boneRot)

                    val offset = component.rigidBodyIndex * 7
                    val array = data.transformArray
                    val px = array[offset + 0]
                    val py = array[offset + 1]
                    val pz = array[offset + 2]
                    val qx = array[offset + 3]
                    val qy = array[offset + 4]
                    val qz = array[offset + 5]
                    val qw = array[offset + 6]

                    println(
                        "PHYSDBG RB_STEP " +
                            "step=${data.debugStepCount} " +
                            "idx=${component.rigidBodyIndex} " +
                            "nodeIndex=$nodeIndex " +
                            "nodeName=${node.nodeName} " +
                            "mode=${component.rigidBodyData.physicsMode} " +
                            "bonePos=(${bonePos.x},${bonePos.y},${bonePos.z}) " +
                            "boneRot=(${boneRot.x},${boneRot.y},${boneRot.z},${boneRot.w}) " +
                            "bodyPos=($px,$py,$pz) " +
                            "bodyRot=($qx,$qy,$qz,$qw)"
                    )

                    if (node.nodeName != null && node.nodeName.startsWith("Skirt_")) {
                        val shapePos = component.rigidBodyData.shapePosition
                        val shapeRot = component.rigidBodyData.shapeRotation
                        println(
                            "PHYSDBG RB_SKIRT " +
                                "step=${data.debugStepCount} " +
                                "idx=${component.rigidBodyIndex} " +
                                "nodeIndex=$nodeIndex " +
                                "nodeName=${node.nodeName} " +
                                "shapePos=(${shapePos.x()},${shapePos.y()},${shapePos.z()}) " +
                                "shapeRot=(${shapeRot.x()},${shapeRot.y()},${shapeRot.z()}) " +
                                "bonePos=(${bonePos.x},${bonePos.y},${bonePos.z}) " +
                                "bodyPos=($px,$py,$pz)"
                        )
                    }
                }
            }
            data.debugStepCount++

            executePhase(instance, UpdatePhase.PhysicsUpdatePost)
            executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        }
    }

    fun debugRender(
        instance: ModelInstanceImpl,
        viewProjectionMatrix: Matrix4fc,
        consumers: VertexConsumerProvider,
        time: Float,
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
        updatePhysics(instance, time)
        UpdatePhase.DebugRender.acquire(viewProjectionMatrix, consumers).use {
            executePhase(instance, it)
        }
    }

    fun updateRenderData(instance: ModelInstanceImpl, time: Float) {
        if (instance.modelData.undirtyNodeCount == nodes.size) {
            return
        }
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        executePhase(instance, UpdatePhase.IkUpdate)
        executePhase(instance, UpdatePhase.InfluenceTransformUpdate)
        executePhase(instance, UpdatePhase.GlobalTransformPropagation)
        updatePhysics(instance, time)
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
        physicsScene?.close()
    }
}
