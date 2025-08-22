package top.fifthlight.blazerod.model

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.PlaneCollisionShape
import com.jme3.bullet.joints.SixDofSpringJoint
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Plane
import com.jme3.math.Transform
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f
import top.fifthlight.blazerod.model.data.ModelMatricesBuffer
import top.fifthlight.blazerod.model.data.MorphTargetBuffer
import top.fifthlight.blazerod.model.data.RenderSkinBuffer
import top.fifthlight.blazerod.model.node.RenderNode
import top.fifthlight.blazerod.model.node.TransformMap
import top.fifthlight.blazerod.model.node.UpdatePhase
import top.fifthlight.blazerod.model.node.markNodeTransformDirty
import top.fifthlight.blazerod.model.resource.CameraTransform
import top.fifthlight.blazerod.physics.PhysicsLibrary
import top.fifthlight.blazerod.util.*
import java.util.function.Consumer

class ModelInstance(val scene: RenderScene) : AbstractRefCount() {
    companion object {
        private val TYPE_ID = Identifier.of("blazerod", "model_instance")
        private const val PHYSICS_FPS = 120f
    }

    override val typeId: Identifier
        get() = TYPE_ID

    val modelData = ModelData(scene)
    internal val physicsData = if (PhysicsLibrary.isPhysicsAvailable && scene.hasPhysics) {
        PhysicsData(scene)
    } else {
        null
    }

    init {
        scene.increaseReferenceCount()
        scene.attachToInstance(this)
        physicsData?.initJoints()
    }

    internal class PhysicsData(scene: RenderScene) : AutoCloseable {
        private val rootNode = scene.rootNode
        private val physicsJoints = scene.physicsJoints

        val world = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT).apply {
            accuracy = 1f / PHYSICS_FPS

            setGravity(JmeVector3f(0f, -9.8f, 0f))

            val groundRigidBody = PhysicsRigidBody(PlaneCollisionShape(Plane(JmeVector3f.UNIT_Y, 0f)), 0f)
            addCollisionObject(groundRigidBody)
            setGroundObject(groundRigidBody)
        }
        var lastPhysicsTime = -1f
        val rigidBodies = Array<PhysicsRigidBody?>(scene.rigidBodyComponents.size) { null }

        fun getRigidBody(index: Int) = rigidBodies[index] ?: error("Rigid body not initialized")

        fun initJoints() = physicsJoints.forEach { jointData ->
            val rootNodeTransform = Matrix4f().apply {
                rootNode.absoluteTransform?.matrix?.get(this)
            }
            val transformMatrix = rootNodeTransform.translate(jointData.position, Matrix4f())
                .rotate(Quaternionf().rotationZYX(jointData.rotation))
                .mul(rootNodeTransform)

            val rigidBodyA = getRigidBody(jointData.rigidBodyAIndex)
            val rigidBodyB = getRigidBody(jointData.rigidBodyBIndex)
            val invA = rigidBodyA.getTransform(Transform())
                .toTransformMatrix()
                .get(Matrix4f())
                .invert()
                .mul(transformMatrix)
            val invB = rigidBodyB.getTransform(Transform())
                .toTransformMatrix()
                .get(Matrix4f())
                .invert()
                .mul(transformMatrix)
            val joint = when (jointData.type) {
                PhysicalJoint.JointType.SPRING_6DOF -> {
                    SixDofSpringJoint(
                        rigidBodyA,
                        rigidBodyB,
                        invA.getTranslation(Vector3f()).toJme(),
                        invB.getTranslation(Vector3f()).toJme(),
                        invA.getUnnormalizedRotation(Quaternionf()).toJme().toRotationMatrix(JmeMatrix3f()),
                        invB.getUnnormalizedRotation(Quaternionf()).toJme().toRotationMatrix(JmeMatrix3f()),
                        true,
                    )
                }
            }
            joint.setLinearLowerLimit(jointData.positionMin.toJme())
            joint.setLinearUpperLimit(jointData.positionMax.toJme())
            joint.setAngularLowerLimit(jointData.rotationMin.toJme())
            joint.setAngularUpperLimit(jointData.rotationMax.toJme())
            // TODO spring
        }

        override fun close() {
            world.destroy()
        }
    }

    class ModelData(scene: RenderScene) : AutoCloseable {
        var undirtyNodeCount = 0

        val transformMaps = scene.nodes.mapToArray { node ->
            TransformMap(node.absoluteTransform)
        }

        val transformDirty = Array(scene.nodes.size) { true }

        val worldTransforms = Array(scene.nodes.size) { Matrix4f() }

        val modelMatricesBuffer = run {
            val buffer = ModelMatricesBuffer(scene)
            buffer.clear()
            CowBuffer.acquire(buffer).also { it.increaseReferenceCount() }
        }

        val skinBuffers = scene.skins.mapIndexed { index, skin ->
            val skinBuffer = RenderSkinBuffer(skin)
            skinBuffer.clear()
            CowBuffer.acquire(skinBuffer).also { it.increaseReferenceCount() }
        }

        val targetBuffers = scene.morphedPrimitiveComponents.mapIndexed { index, component ->
            val primitive = component.primitive
            val targets = primitive.targets!!
            val targetBuffers = MorphTargetBuffer(targets)
            for (targetGroup in primitive.targetGroups) {
                fun processGroup(index: Int?, channel: MorphTargetBuffer.WeightChannel, weight: Float) =
                    index?.let {
                        channel[index] = weight
                    }
                processGroup(targetGroup.position, targetBuffers.positionChannel, targetGroup.weight)
                processGroup(targetGroup.color, targetBuffers.colorChannel, targetGroup.weight)
                processGroup(targetGroup.texCoord, targetBuffers.texCoordChannel, targetGroup.weight)
            }
            CowBuffer.acquire(targetBuffers).also { it.increaseReferenceCount() }
        }

        val cameraTransforms = scene.cameras.map { CameraTransform.of(it.camera) }

        val ikEnabled = Array(scene.ikTargetComponents.size) { true }

        override fun close() {
            modelMatricesBuffer.decreaseReferenceCount()
            skinBuffers.forEach { it.decreaseReferenceCount() }
            targetBuffers.forEach { it.decreaseReferenceCount() }
        }
    }

    fun clearTransform() {
        modelData.undirtyNodeCount = 0
        for (i in scene.nodes.indices) {
            modelData.transformMaps[i].clearFrom(TransformId.ABSOLUTE.next)
            modelData.transformDirty[i] = true
        }
    }

    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, matrix: Matrix4f) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.setMatrix(transformId, matrix)
    }

    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, updater: Consumer<NodeTransform.Matrix>) =
        setTransformMatrix(nodeIndex, transformId) { updater.accept(this) }

    fun setTransformMatrix(nodeIndex: Int, transformId: TransformId, updater: NodeTransform.Matrix.() -> Unit) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.updateMatrix(transformId, updater)
    }

    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, decomposed: NodeTransformView.Decomposed) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.setMatrix(transformId, decomposed)
    }

    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, updater: Consumer<NodeTransform.Decomposed>) =
        setTransformDecomposed(nodeIndex, transformId) { updater.accept(this) }

    fun setTransformDecomposed(nodeIndex: Int, transformId: TransformId, updater: NodeTransform.Decomposed.() -> Unit) {
        markNodeTransformDirty(scene.nodes[nodeIndex])
        val transform = modelData.transformMaps[nodeIndex]
        transform.updateDecomposed(transformId, updater)
    }

    fun setIkEnabled(index: Int, enabled: Boolean) {
        val prevEnabled = modelData.ikEnabled[index]
        modelData.ikEnabled[index] = enabled
        if (prevEnabled && !enabled) {
            val component = scene.ikTargetComponents[index]
            for (chain in component.chains) {
                markNodeTransformDirty(scene.nodes[chain.nodeIndex])
                val transform = modelData.transformMaps[chain.nodeIndex]
                transform.clearFrom(component.transformId)
            }
        }
    }

    fun setGroupWeight(morphedPrimitiveIndex: Int, targetGroupIndex: Int, weight: Float) {
        val primitiveComponent = scene.morphedPrimitiveComponents[morphedPrimitiveIndex]
        val group = primitiveComponent.primitive.targetGroups[targetGroupIndex]
        val weightsIndex = requireNotNull(primitiveComponent.morphedPrimitiveIndex) {
            "Component $primitiveComponent don't have target? Check model loader"
        }
        val weights = modelData.targetBuffers[weightsIndex]
        weights.edit {
            group.position?.let { positionChannel[it] = weight }
            group.color?.let { colorChannel[it] = weight }
            group.texCoord?.let { texCoordChannel[it] = weight }
        }
    }

    fun updateCamera() {
        scene.updateCamera(this)
    }

    fun debugRender(viewProjectionMatrix: Matrix4fc, consumers: VertexConsumerProvider, time: Float) {
        scene.debugRender(this, viewProjectionMatrix, consumers, time)
    }

    fun updateRenderData(time: Float) {
        scene.updateRenderData(this, time)
    }

    internal fun updateNodeTransform(nodeIndex: Int) {
        val node = scene.nodes[nodeIndex]
        updateNodeTransform(node)
    }

    internal fun updateNodeTransform(node: RenderNode) {
        if (modelData.undirtyNodeCount == scene.nodes.size) {
            return
        }
        node.update(UpdatePhase.GlobalTransformPropagation, this)
        for (child in node.children) {
            updateNodeTransform(child)
        }
    }

    fun createRenderTask(
        modelViewMatrix: Matrix4fc,
        light: Int,
    ): RenderTask {
        return RenderTask.acquire(
            instance = this,
            modelViewMatrix = modelViewMatrix,
            light = light,
            modelMatricesBuffer = modelData.modelMatricesBuffer.copy(),
            skinBuffer = modelData.skinBuffers.copy(),
            morphTargetBuffer = modelData.targetBuffers.copy().also { buffer ->
                // Upload indices don't change the actual data
                buffer.forEach {
                    it.content.uploadIndices()
                }
            },
        )
    }

    override fun onClosed() {
        scene.decreaseReferenceCount()
        modelData.close()
        physicsData?.close()
    }
}
