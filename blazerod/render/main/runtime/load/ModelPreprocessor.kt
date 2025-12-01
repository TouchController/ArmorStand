package top.fifthlight.blazerod.runtime.load

import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import kotlinx.coroutines.*
import top.fifthlight.blazerod.api.resource.RenderExpression
import top.fifthlight.blazerod.api.resource.RenderExpressionGroup
import org.joml.Vector3f

import top.fifthlight.blazerod.extension.NativeImageExt
import top.fifthlight.blazerod.extension.TextureFormatExt
import top.fifthlight.blazerod.model.*
import top.fifthlight.blazerod.render.BlazerodVertexFormatElements
import top.fifthlight.blazerod.render.BlazerodVertexFormats

// ... (rest of the code remains the same)

    private fun loadPhysicalJoints(modelPhysicalJoints: List<PhysicalJoint>) =
        modelPhysicalJoints.mapIndexedNotNull { index, joint ->
            val rigidBodyAIndex = rigidBodyIdToIndexMap[joint.rigidBodyA] ?: return@mapIndexedNotNull null
            val rigidBodyBIndex = rigidBodyIdToIndexMap[joint.rigidBodyB] ?: return@mapIndexedNotNull null

            val name = joint.name ?: ""

            val position = joint.position
            val rotation = joint.rotation
            val positionMin = joint.positionMin
            val positionMax = joint.positionMax
            val rotationMin = Vector3f(joint.rotationMin)
            val rotationMax = Vector3f(joint.rotationMax)
            val positionSpring = Vector3f(joint.positionSpring)
            val rotationSpring = Vector3f(joint.rotationSpring)

            // Heuristic tweak: many PMX skirts use extremely tiny angle limits (|angle| < 0.5°)
            // and zero rotation springs. In Bullet with our gravity/scale this makes the skirt
            // behave like a rigid cage that gets pushed around instead of a flexible cloth.
            // For joints whose name starts with "Skirt_", relax the limits a bit and add a
            // gentle restoring spring so the skirt can bend and fall back naturally.
            if (name.startsWith("Skirt_")) {
                val tinyLimit = 0.02f // ~1.1 degrees
                val allTiny =
                    kotlin.math.abs(rotationMin.x) < tinyLimit && kotlin.math.abs(rotationMax.x) < tinyLimit &&
                        kotlin.math.abs(rotationMin.y) < tinyLimit && kotlin.math.abs(rotationMax.y) < tinyLimit &&
                        kotlin.math.abs(rotationMin.z) < tinyLimit && kotlin.math.abs(rotationMax.z) < tinyLimit

                if (allTiny) {
                    // Allow the skirt segment to swing by roughly +/- 20 degrees on all axes.
                    val base = 0.35f
                    rotationMin.set(-base, -base, -base)
                    rotationMax.set(base, base, base)
                }

                if (rotationSpring.x == 0f && rotationSpring.y == 0f && rotationSpring.z == 0f) {
                    // Small rotational spring mostly around lateral axes (Y/Z). This is intentionally
                    // much weaker than hair springs; Bullet-side scaling and damping will further
                    // soften it.
                    rotationSpring.set(0f, 0.5f, 0.5f)
                }
            }

            if (index < 64) {
                println(
                    "PHYSDBG JOINT_KT " +
                        "idx=$index " +
                        "name=$name " +
                        "A=$rigidBodyAIndex " +
                        "B=$rigidBodyBIndex " +
                        "pos=(${position.x()},${position.y()},${position.z()}) " +
                        "rot=(${rotation.x()},${rotation.y()},${rotation.z()}) " +
                        "posMin=(${positionMin.x()},${positionMin.y()},${positionMin.z()}) " +
                        "posMax=(${positionMax.x()},${positionMax.y()},${positionMax.z()}) " +
                        "rotMin=(${rotationMin.x},${rotationMin.y},${rotationMin.z}) " +
                        "rotMax=(${rotationMax.x},${rotationMax.y},${rotationMax.z}) " +
                        "posSpring=(${positionSpring.x},${positionSpring.y},${positionSpring.z}) " +
                        "rotSpring=(${rotationSpring.x},${rotationSpring.y},${rotationSpring.z})"
                )
            }

            RenderPhysicsJoint(
                name = name,
                type = joint.type,
                rigidBodyAIndex = rigidBodyAIndex,
                rigidBodyBIndex = rigidBodyBIndex,
                position = position,
                rotation = rotation,
                positionMin = positionMin,
                positionMax = positionMax,
                rotationMin = rotationMin,
                rotationMax = rotationMax,
                positionSpring = positionSpring,
                rotationSpring = rotationSpring,
            )
        }

    // ... (rest of the code remains the same)
            transform = null,
            components = listOf(),
            childrenIndices = scene.nodes.map { loadNode(it) },
        )
        val rootNodeIndex = nodes.size
        nodes.add(rootNode)
        val (expressions, expressionGroups) = loadExpressions(expressions)
        val physicalJoints = loadPhysicalJoints(model.physicalJoints)
        return PreProcessModelLoadInfo(
            textures = textures,
            indexBuffers = indexBuffers,
            vertexBuffers = vertexBuffers,
            primitiveInfos = primitiveInfos,
            nodes = nodes,
            rootNodeIndex = rootNodeIndex,
            skins = skinsList,
            morphTargetInfos = morphTargetInfos,
            expressions = expressions,
            expressionGroups = expressionGroups,
            physicalJoints = physicalJoints,
            renderTransform = scene.transform,
        )
    }

    private fun loadModel(): PreProcessModelLoadInfo? {
        loadSkins()
        val scene = model.defaultScene ?: model.scenes.firstOrNull() ?: return null
        return loadScene(scene, model.expressions)
    }

    companion object {
        fun preprocess(
            scope: CoroutineScope,
            loadDispatcher: CoroutineDispatcher,
            model: Model,
        ) = ModelPreprocessor(scope, loadDispatcher, model).loadModel()
    }
}