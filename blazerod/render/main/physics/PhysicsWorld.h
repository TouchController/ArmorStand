#ifndef BLAZEROD_PHYSICSWORLD_H
#define BLAZEROD_PHYSICSWORLD_H

#include <btBulletCollisionCommon.h>
#include <btBulletDynamicsCommon.h>

#include <memory>
#include <vector>

#include "blazerod/render/main/physics/PhysicsScene.h"

namespace blazerod::physics {
struct RigidBodyData {
    std::unique_ptr<btCollisionShape> shape;
    std::unique_ptr<btMotionState> motion_state;
    std::unique_ptr<btRigidBody> rigidbody;
};

class PhysicsWorld {
   private:
    std::unique_ptr<btBroadphaseInterface> broadphase;
    std::unique_ptr<btDefaultCollisionConfiguration> collision_config;
    std::unique_ptr<btCollisionDispatcher> dispatcher;
    std::unique_ptr<btSequentialImpulseConstraintSolver> solver;
    std::unique_ptr<btDiscreteDynamicsWorld> world;

    std::unique_ptr<btCollisionShape> ground_shape;
    std::unique_ptr<btMotionState> ground_motion_state;
    std::unique_ptr<btRigidBody> ground_rigidbody;
    std::unique_ptr<btOverlapFilterCallback> filter_callback;

    std::vector<RigidBodyData> rigidbodies;
    std::vector<std::unique_ptr<btTypedConstraint>> joints;

    std::vector<float> transform_buffer;

    friend class PhysicsMotionState;

   public:
    PhysicsWorld(const PhysicsScene& scene, size_t initial_transform_count, float* initial_transform);
    ~PhysicsWorld();

    std::vector<float>& GetTransformBuffer() { return transform_buffer; }
    void Step(float delta_time, float max_sub_steps, float fixed_time_step);
};
}  // namespace blazerod::physics

#endif  // BLAZEROD_PHYSICSWORLD_H
