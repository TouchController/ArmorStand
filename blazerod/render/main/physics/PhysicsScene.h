#ifndef BLAZEROD_PHYSICSSCENE_H
#define BLAZEROD_PHYSICSSCENE_H

#include <cstddef>
#include <cstdint>
#include <vector>

namespace blazerod::physics {

enum ShapeType {
    SPHERE = 0,
    BOX = 1,
    CAPSULE = 2,
};

enum PhysicsMode {
    FOLLOW_BONE = 0,
    PHYSICS = 1,
    PHYSICS_PLUS_BONE = 2,
};

enum JointType {
    SPRING_6DOF = 0,
};

struct Vector3f {
    float x;
    float y;
    float z;
};

struct RigidBody {
    uint32_t collision_group;
    uint32_t collision_mask;
    ShapeType shape_type;
    Vector3f shape_size;
    Vector3f shape_position;
    Vector3f shape_rotation;
    float mass;
    float move_attenuation;
    float rotation_damping;
    float repulsion;
    float friction_force;
    PhysicsMode physics_mode;
};

struct Joint {
    JointType type;
    uint32_t rigidbody_a_index;
    uint32_t rigidbody_b_index;
    Vector3f position;
    Vector3f rotation;
    Vector3f position_min;
    Vector3f position_max;
    Vector3f rotation_min;
    Vector3f rotation_max;
    Vector3f position_spring;
    Vector3f rotation_spring;
};

class PhysicsScene {
   private:
    const std::vector<RigidBody> rigidbodies;
    const std::vector<Joint> joints;

   public:
    PhysicsScene(size_t rigidbody_count, void* rigidbodies, size_t joints_size, void* joints);

    const std::vector<RigidBody>& GetRigidBodies() const { return rigidbodies; }
    const std::vector<Joint>& GetJoints() const { return joints; }
};
}  // namespace blazerod::physics

#endif  // BLAZEROD_PHYSICSSCENE_H
