package top.fifthlight.blazerod.model

sealed class NodeComponent {
    enum class Type(
        val requireMesh: Boolean,
        val singleInstanceOnly: Boolean,
    ) {
        MESH(
            requireMesh = false,
            singleInstanceOnly = false,
        ),
        SKIN(
            requireMesh = true,
            singleInstanceOnly = false,
        ),
        CAMERA(
            requireMesh = false,
            singleInstanceOnly = false,
        ),
        IK_EFFECTOR(
            requireMesh = false,
            singleInstanceOnly = false,
        ),
        INFLUENCE_SOURCE(
            requireMesh = false,
            singleInstanceOnly = false,
        ),
        RIGID_BODY(
            requireMesh = false,
            singleInstanceOnly = false,
        );

        companion object {
            val requireMesh = entries.filter { it.requireMesh }
            val onlyHaveOne = entries.filter { it.singleInstanceOnly }
        }
    }

    abstract val type: Type

    data class MeshComponent(val mesh: Mesh): NodeComponent(){
        override val type: Type
            get() = Type.MESH
    }

    data class SkinComponent(
        val skin: Skin,
        val meshIds: List<MeshId>,
    ) : NodeComponent() {
        override val type: Type
            get() = Type.SKIN
    }

    data class CameraComponent(val camera: Camera): NodeComponent() {
        override val type: Type
            get() = Type.CAMERA
    }

    data class IkTargetComponent(
        val ikTarget: IkTarget,
        val transformId: TransformId,
    ): NodeComponent() {
        override val type: Type
            get() = Type.IK_EFFECTOR
    }

    data class InfluenceSourceComponent(
        val influence: Influence,
        val transformId: TransformId,
    ): NodeComponent() {
        override val type: Type
            get() = Type.INFLUENCE_SOURCE
    }

    data class RigidBodyComponent(
        val rigidBodyId: RigidBodyId,
        val rigidBody: RigidBody,
    ) : NodeComponent() {
        override val type: Type
            get() = Type.RIGID_BODY
    }
}