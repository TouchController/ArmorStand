#include "PhysicsWorld.h"

#include <stdexcept>
#include <vector>

#include "blazerod/render/main/physics/PhysicsScene.h"

namespace blazerod::physics {

struct PhysicsFilterCallback : public btOverlapFilterCallback {
    btBroadphaseProxy* ground_proxy;

    bool needBroadphaseCollision(btBroadphaseProxy* proxy0, btBroadphaseProxy* proxy1) const override {
        bool is_ground = proxy0 == ground_proxy || proxy1 == ground_proxy;
        bool proxy0_collides = (proxy0->m_collisionFilterGroup & proxy1->m_collisionFilterMask) != 0;
        bool proxy1_collides = (proxy0->m_collisionFilterMask & proxy1->m_collisionFilterGroup) != 0;
        return (proxy0_collides && proxy1_collides) || is_ground;
    }
};

class FollowBoneMotionState : public btMotionState {
   private:
    PhysicsWorld* world;
    size_t rigidbody_index;

   public:
    FollowBoneMotionState(PhysicsWorld* world, size_t rigidbody_index)
        : world(world), rigidbody_index(rigidbody_index) {}
    void getWorldTransform(btTransform& world_transform) const override {
        world_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
    }

    void setWorldTransform(const btTransform& world_transform) override {}
};

class PhysicsMotionState : public btMotionState {
   private:
    PhysicsWorld* world;
    size_t rigidbody_index;

   public:
    PhysicsMotionState(PhysicsWorld* world, size_t rigidbody_index) : world(world), rigidbody_index(rigidbody_index) {}
    void getWorldTransform(btTransform& world_transform) const override {
        world_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
    }
    void setWorldTransform(const btTransform& world_transform) override {
        world_transform.getOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
    }
};

class PhysicsPlusBoneDynamicMotionState : public btMotionState {
   private:
    PhysicsWorld* world;
    size_t rigidbody_index;

   public:
    PhysicsPlusBoneDynamicMotionState(PhysicsWorld* world, size_t rigidbody_index)
        : world(world), rigidbody_index(rigidbody_index) {}
    void getWorldTransform(btTransform& world_transform) const override {
        world_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
    }
    void setWorldTransform(const btTransform& world_transform) override {
        btTransform original_transform;
        original_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
        btTransform final_transform = world_transform;
        final_transform.setOrigin(original_transform.getOrigin());
        final_transform.getOpenGLMatrix(&world->GetTransformBuffer()[this->rigidbody_index * 16]);
    }
};

PhysicsWorld::PhysicsWorld(const PhysicsScene& scene, size_t initial_transform_count, float* initial_transform)
    : transform_buffer(initial_transform, initial_transform + initial_transform_count) {
    this->broadphase = std::make_unique<btDbvtBroadphase>();
    this->collision_config = std::make_unique<btDefaultCollisionConfiguration>();
    this->dispatcher = std::make_unique<btCollisionDispatcher>(this->collision_config.get());
    this->solver = std::make_unique<btSequentialImpulseConstraintSolver>();
    this->world = std::make_unique<btDiscreteDynamicsWorld>(this->dispatcher.get(), this->broadphase.get(),
                                                            this->solver.get(), this->collision_config.get());
    this->world->setGravity(btVector3(0, -9.81, 0));

    this->ground_shape = std::make_unique<btStaticPlaneShape>(btVector3(0, 1, 0), 0.0f);
    btTransform ground_transform;
    ground_transform.setIdentity();
    this->ground_motion_state = std::make_unique<btDefaultMotionState>(ground_transform);
    this->ground_rigidbody =
        std::make_unique<btRigidBody>(0.0f, this->ground_motion_state.get(), this->ground_shape.get());
    this->world->addRigidBody(this->ground_rigidbody.get());

    auto filter_callback = std::make_unique<PhysicsFilterCallback>();
    filter_callback->ground_proxy = this->ground_rigidbody->getBroadphaseProxy();
    this->world->getPairCache()->setOverlapFilterCallback(filter_callback.get());
    this->filter_callback = std::move(filter_callback);

    const auto& rigidbodies = scene.GetRigidBodies();
    if (transform_buffer.size() != rigidbodies.size() * 16) {
        throw std::invalid_argument("Transform buffer size does not match rigidbody count");
    }
    this->rigidbodies.reserve(rigidbodies.size());

    size_t rigidbody_count = 0;
    for (const RigidBody& rigidbody_item : rigidbodies) {
        size_t rigidbody_index = rigidbody_count++;
        RigidBodyData rigidbody_data;

        std::unique_ptr<btCollisionShape> shape;
        switch (rigidbody_item.shape_type) {
            case ShapeType::BOX: {
                shape = std::make_unique<btBoxShape>(
                    btVector3(rigidbody_item.shape_size.x, rigidbody_item.shape_size.y, rigidbody_item.shape_size.z));
                break;
            };

            case ShapeType::SPHERE: {
                shape = std::make_unique<btSphereShape>(rigidbody_item.shape_size.x);
                break;
            };

            case ShapeType::CAPSULE: {
                shape = std::make_unique<btCapsuleShape>(rigidbody_item.shape_size.x, rigidbody_item.shape_size.y);
                break;
            };

            default: {
                throw std::invalid_argument("Invalid shape");
            }
        }

        float mass = rigidbody_item.physics_mode == PhysicsMode::FOLLOW_BONE ? 0.0f : rigidbody_item.mass;
        btVector3 local_inertia(0, 0, 0);
        if (mass != 0.0f) {
            shape->calculateLocalInertia(mass, local_inertia);
        }

        std::unique_ptr<btMotionState> motion_state;
        switch (rigidbody_item.physics_mode) {
            case FOLLOW_BONE: {
                motion_state = std::make_unique<FollowBoneMotionState>(this, rigidbody_index);
                break;
            }
            case PHYSICS: {
                motion_state = std::make_unique<PhysicsMotionState>(this, rigidbody_index);
                break;
            }
            case PHYSICS_PLUS_BONE: {
                motion_state = std::make_unique<PhysicsPlusBoneDynamicMotionState>(this, rigidbody_index);
                break;
            }

            default: {
                throw std::invalid_argument("Invalid physics mode");
            }
        }

        btRigidBody::btRigidBodyConstructionInfo rigidbody_info(rigidbody_item.mass, motion_state.get(), shape.get(),
                                                                local_inertia);
        rigidbody_info.m_linearDamping = rigidbody_item.move_attenuation;
        rigidbody_info.m_angularDamping = rigidbody_item.rotation_damping;
        rigidbody_info.m_restitution = rigidbody_item.repulsion;
        rigidbody_info.m_friction = rigidbody_item.friction_force;
        rigidbody_info.m_additionalDamping = true;

        auto rigidbody = std::make_unique<btRigidBody>(rigidbody_info);
        this->world->addRigidBody(rigidbody.get());
        rigidbody->setActivationState(DISABLE_DEACTIVATION);
        if (rigidbody_item.physics_mode == PhysicsMode::FOLLOW_BONE) {
            rigidbody->setCollisionFlags(rigidbody->getCollisionFlags() | btCollisionObject::CF_KINEMATIC_OBJECT);
        }

        rigidbody_data.shape = std::move(shape);
        rigidbody_data.motion_state = std::move(motion_state);
        rigidbody_data.rigidbody = std::move(rigidbody);
        this->rigidbodies.push_back(std::move(rigidbody_data));
    }

    const auto& joints = scene.GetJoints();
    this->joints.reserve(joints.size());

    for (const Joint& joint_item : joints) {
        btMatrix3x3 rotMat;
        rotMat.setEulerZYX(joint_item.rotation.x, joint_item.rotation.y, joint_item.rotation.z);

        btTransform transform;
        transform.setIdentity();
        transform.setOrigin(btVector3(joint_item.position.x, joint_item.position.y, joint_item.position.z));
        transform.setBasis(rotMat);

        size_t rigidbody_a_index = joint_item.rigidbody_a_index;
        if (rigidbody_a_index >= this->rigidbodies.size()) {
            throw std::invalid_argument("Invalid rigidbody index");
        }
        const auto& rigidbody_a = this->rigidbodies[joint_item.rigidbody_a_index];
        size_t rigidbody_b_index = joint_item.rigidbody_b_index;
        if (rigidbody_b_index >= this->rigidbodies.size()) {
            throw std::invalid_argument("Invalid rigidbody index");
        }
        const auto& rigidbody_b = this->rigidbodies[joint_item.rigidbody_b_index];

        btTransform inverse_a = rigidbody_a.rigidbody->getWorldTransform().inverse();
        btTransform inverse_b = rigidbody_b.rigidbody->getWorldTransform().inverse();
        inverse_a *= transform;
        inverse_b *= transform;

        auto constraint = std::make_unique<btGeneric6DofSpringConstraint>(
            *rigidbody_a.rigidbody, *rigidbody_b.rigidbody, inverse_a, inverse_b, true);
        constraint->setLinearLowerLimit(
            btVector3(joint_item.position_min.x, joint_item.position_min.y, joint_item.position_min.z));
        constraint->setLinearUpperLimit(
            btVector3(joint_item.position_max.x, joint_item.position_max.y, joint_item.position_max.z));

        constraint->setAngularLowerLimit(
            btVector3(joint_item.rotation_min.x, joint_item.rotation_min.y, joint_item.rotation_min.z));
        constraint->setAngularUpperLimit(
            btVector3(joint_item.rotation_max.x, joint_item.rotation_max.y, joint_item.rotation_max.z));

        if (joint_item.position_spring.x != 0.0f) {
            constraint->enableSpring(0, true);
            constraint->setStiffness(0, joint_item.position_spring.x);
        }
        if (joint_item.position_spring.y != 0.0f) {
            constraint->enableSpring(1, true);
            constraint->setStiffness(1, joint_item.position_spring.y);
        }
        if (joint_item.position_spring.z != 0.0f) {
            constraint->enableSpring(2, true);
            constraint->setStiffness(2, -joint_item.position_spring.z);
        }
        if (joint_item.rotation_spring.x != 0.0f) {
            constraint->enableSpring(3, true);
            constraint->setStiffness(3, joint_item.rotation_spring.x);
        }
        if (joint_item.rotation_spring.y != 0.0f) {
            constraint->enableSpring(4, true);
            constraint->setStiffness(4, joint_item.rotation_spring.y);
        }
        if (joint_item.rotation_spring.z != 0.0f) {
            constraint->enableSpring(5, true);
            constraint->setStiffness(5, joint_item.rotation_spring.z);
        }

        this->world->addConstraint(constraint.get(), true);
        this->joints.push_back(std::move(constraint));
    }
}

PhysicsWorld::~PhysicsWorld() {
    this->world->removeRigidBody(this->ground_rigidbody.get());
    for (auto& joint : this->joints) {
        this->world->removeConstraint(joint.get());
    }
    for (auto& rigidbody : this->rigidbodies) {
        this->world->removeRigidBody(rigidbody.rigidbody.get());
    }
}

void PhysicsWorld::Step(float delta_time, float max_sub_steps, float fixed_time_step) {
    this->world->stepSimulation(delta_time, max_sub_steps, fixed_time_step);
}

}  // namespace blazerod::physics
