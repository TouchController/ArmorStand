#ifndef BLAZEROD_PHYSICSWORLD_H
#define BLAZEROD_PHYSICSWORLD_H

#include <btBulletCollisionCommon.h>
#include <btBulletDynamicsCommon.h>

#include <memory>
#include <vector>

#include "blazerod/render/main/physics/PhysicsScene.h"

namespace blazerod::physics {

class PhysicsWorld;

class PhysicsMotionState : public btMotionState {
   protected:
    btTransform transform;
    btTransform from_node_to_world;
    btTransform from_world_to_node;
    bool isDirty = false;

   public:
    PhysicsMotionState(btTransform& initial_transform, const Vector3f& position, const Vector3f& rotation);

    void getWorldTransform(btTransform& world_transform) const override;
    void setWorldTransform(const btTransform& world_transform) override;
    bool IsDirty() const { return isDirty; }

    virtual void GetFromWorld(const PhysicsWorld* world, size_t rigidbody_index) = 0;
    virtual void SetToWorld(PhysicsWorld* world, size_t rigidbody_index) = 0;
};

struct RigidBodyData {
    std::unique_ptr<btCollisionShape> shape;
    std::unique_ptr<PhysicsMotionState> motion_state;
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

    std::unique_ptr<float[]> transform_buffer;

   public:
    PhysicsWorld(const PhysicsScene& scene, size_t initial_transform_count, float* initial_transform);
    ~PhysicsWorld();

    float* GetTransformBuffer() const { return transform_buffer.get(); }
    size_t GetTransformBufferSize() { return rigidbodies.size() * 16 * sizeof(float); }
    void Step(float delta_time, int max_sub_steps, float fixed_time_step);
};
}  // namespace blazerod::physics

#endif  // BLAZEROD_PHYSICSWORLD_H
