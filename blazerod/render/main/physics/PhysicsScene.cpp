#include "PhysicsScene.h"

#include <sys/types.h>

#include <cstring>
#include <stdexcept>

namespace blazerod::physics {
template <typename T>
static void CopyField(T& field, void* data) {
    std::memcpy(&field, data, sizeof(field));
}

template <typename T>
static void CopyEnumField(T& field, void* data) {
    static_assert(std::is_enum_v<T>, "CopyEnumField only works with enum types");
    uint32_t enum_value;
    CopyField(enum_value, data);
    field = static_cast<T>(enum_value);
}

static void CopyVector3fField(Vector3f& vector, void* data) {
    uint8_t* ptr = static_cast<uint8_t*>(data);
    CopyField(vector.x, ptr + 0);
    CopyField(vector.y, ptr + 4);
    CopyField(vector.z, ptr + 8);
}

static std::vector<RigidBody> DeserializeRigidbodies(size_t size, void* data) {
    const size_t rigidbody_size = 72;
    if (size == 0) {
        throw std::invalid_argument("Empty rigidbody data");
    }
    if (size % rigidbody_size != 0) {
        throw std::invalid_argument("Invalid rigidbody size");
    }

    size_t rigidbody_count = size / rigidbody_size;
    std::vector<RigidBody> rigidbodies;
    rigidbodies.reserve(rigidbody_count);

    for (size_t i = 0; i < rigidbody_count; i++) {
        uint8_t* rigidbody_data = static_cast<uint8_t*>(data) + i * rigidbody_size;
        RigidBody rigidbody;

        CopyField(rigidbody.collision_group, rigidbody_data + 0);
        CopyField(rigidbody.collision_mask, rigidbody_data + 4);
        CopyEnumField(rigidbody.shape_type, rigidbody_data + 8);
        CopyEnumField(rigidbody.physics_mode, rigidbody_data + 12);
        CopyVector3fField(rigidbody.shape_size, rigidbody_data + 16);
        CopyVector3fField(rigidbody.shape_position, rigidbody_data + 28);
        CopyVector3fField(rigidbody.shape_rotation, rigidbody_data + 40);
        CopyField(rigidbody.mass, rigidbody_data + 52);
        CopyField(rigidbody.move_attenuation, rigidbody_data + 56);
        CopyField(rigidbody.rotation_damping, rigidbody_data + 60);
        CopyField(rigidbody.repulsion, rigidbody_data + 64);
        CopyField(rigidbody.friction_force, rigidbody_data + 68);

        rigidbodies.push_back(rigidbody);
    }

    return rigidbodies;
}

static std::vector<Joint> DeserializeJoints(size_t size, void* data) {
    const size_t joint_size = 108;
    if (size % joint_size != 0) {
        throw std::invalid_argument("Invalid joint size");
    }

    size_t joint_count = size / joint_size;
    std::vector<Joint> joints;
    joints.reserve(joint_count);

    for (size_t i = 0; i < joint_count; i++) {
        uint8_t* joint_data = static_cast<uint8_t*>(data) + i * joint_size;
        Joint joint;

        CopyEnumField(joint.type, joint_data + 0);
        CopyField(joint.rigidbody_a_index, joint_data + 4);
        CopyField(joint.rigidbody_b_index, joint_data + 8);
        CopyVector3fField(joint.position, joint_data + 12);
        CopyVector3fField(joint.rotation, joint_data + 24);
        CopyVector3fField(joint.position_min, joint_data + 36);
        CopyVector3fField(joint.position_max, joint_data + 48);
        CopyVector3fField(joint.rotation_min, joint_data + 60);
        CopyVector3fField(joint.rotation_max, joint_data + 72);
        CopyVector3fField(joint.position_spring, joint_data + 84);
        CopyVector3fField(joint.rotation_spring, joint_data + 96);

        joints.push_back(joint);
    }

    return joints;
}

PhysicsScene::PhysicsScene(size_t rigidbody_size, void* rigidbodies, size_t joints_size, void* joints)
    : rigidbodies(DeserializeRigidbodies(rigidbody_size, rigidbodies)),
      joints(DeserializeJoints(joints_size, joints)) {}
}  // namespace blazerod::physics
