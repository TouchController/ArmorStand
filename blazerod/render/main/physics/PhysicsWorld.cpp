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

void PhysicsMotionState::getWorldTransform(btTransform& world_transform) const { world_transform = this->transform; }

void PhysicsMotionState::setWorldTransform(const btTransform& world_transform) {
    this->transform = world_transform;
    isDirty = true;
}

PhysicsMotionState::PhysicsMotionState(btTransform& initial_transform, const Vector3f& position,
                                       const Vector3f& rotation) {
    transform.setIdentity();

    btMatrix3x3 rotation_matrix;
    rotation_matrix.setEulerZYX(rotation.x, rotation.y, rotation.z);
    btVector3 pos(position.x, position.y, position.z);
    btTransform rigidbody_transform;
    rigidbody_transform.setIdentity();
    rigidbody_transform.setOrigin(pos);
    rigidbody_transform.setBasis(rotation_matrix);

    from_node_to_world.mult(rigidbody_transform, initial_transform.inverse());
    from_world_to_node = from_node_to_world.inverse();
}

class FollowBoneObjectMotionState : public PhysicsMotionState {
   public:
    FollowBoneObjectMotionState(btTransform& initial_transform, const Vector3f& position, const Vector3f& rotation)
        : PhysicsMotionState(initial_transform, position, rotation) {}

    void GetFromWorld(const PhysicsWorld* world, size_t rigidbody_index) override {
        btTransform node_transform;
        node_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[rigidbody_index * 16]);
        this->transform.mult(node_transform, this->from_node_to_world);
    }

    void setWorldTransform(const btTransform& world_transform) override {}

    void SetToWorld(PhysicsWorld* world, size_t rigidbody_index) override { this->isDirty = false; }
};

class PhysicsObjectMotionState : public PhysicsMotionState {
   public:
    PhysicsObjectMotionState(btTransform& initial_transform, const Vector3f& position, const Vector3f& rotation)
        : PhysicsMotionState(initial_transform, position, rotation) {}

    void GetFromWorld(const PhysicsWorld* world, size_t rigidbody_index) override {
        btTransform node_transform;
        node_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[rigidbody_index * 16]);
        this->transform.mult(node_transform, this->from_node_to_world);
    }

    void SetToWorld(PhysicsWorld* world, size_t rigidbody_index) override {
        this->isDirty = false;
        btTransform node_transform;
        node_transform.mult(this->transform, this->from_world_to_node);
        node_transform.getOpenGLMatrix(&world->GetTransformBuffer()[rigidbody_index * 16]);
    }
};

class PhysicsPlusBoneObjectMotionState : public PhysicsMotionState {
   private:
    btVector3 origin;

   public:
    PhysicsPlusBoneObjectMotionState(btTransform& initial_transform, const Vector3f& position, const Vector3f& rotation)
        : PhysicsMotionState(initial_transform, position, rotation) {}

    void GetFromWorld(const PhysicsWorld* world, size_t rigidbody_index) override {
        btTransform node_transform;
        node_transform.setFromOpenGLMatrix(&world->GetTransformBuffer()[rigidbody_index * 16]);
        this->transform.mult(node_transform, this->from_node_to_world);
        this->origin = transform.getOrigin();
    }

    void SetToWorld(PhysicsWorld* world, size_t rigidbody_index) override {
        this->isDirty = false;
        btTransform world_transform = this->transform;
        world_transform.setOrigin(this->origin);
        btTransform node_transform;
        node_transform.mult(world_transform, this->from_world_to_node);
        node_transform.getOpenGLMatrix(&world->GetTransformBuffer()[rigidbody_index * 16]);
    }
};

PhysicsWorld::PhysicsWorld(const PhysicsScene& scene, size_t initial_transform_count, float* initial_transform) {
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
    if (initial_transform_count != rigidbodies.size() * 16) {
        throw std::invalid_argument("Initial transform count must match rigidbody count");
    }
    transform_buffer = std::make_unique<float[]>(initial_transform_count);
    memcpy(transform_buffer.get(), initial_transform, initial_transform_count * sizeof(float));
    this->rigidbodies.reserve(rigidbodies.size());

    size_t rigidbody_count = 0;
    for (const RigidBody& rigidbody_item : rigidbodies) {
        size_t rigidbody_index = rigidbody_count++;
        RigidBodyData rigidbody_data;

        btTransform transform;
        transform.setFromOpenGLMatrix(&initial_transform[rigidbody_index * 16]);

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

        std::unique_ptr<PhysicsMotionState> motion_state;
        switch (rigidbody_item.physics_mode) {
            case FOLLOW_BONE: {
                motion_state = std::make_unique<FollowBoneObjectMotionState>(transform, rigidbody_item.shape_position,
                                                                             rigidbody_item.shape_rotation);
                break;
            }
            case PHYSICS: {
                motion_state = std::make_unique<PhysicsObjectMotionState>(transform, rigidbody_item.shape_position,
                                                                          rigidbody_item.shape_rotation);
                break;
            }
            case PHYSICS_PLUS_BONE: {
                motion_state = std::make_unique<PhysicsPlusBoneObjectMotionState>(
                    transform, rigidbody_item.shape_position, rigidbody_item.shape_rotation);
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

    size_t joint_count = 0;
    for (const Joint& joint_item : joints) {
        size_t joint_index = joint_count++;
        btMatrix3x3 rotation_matrix;
        rotation_matrix.setEulerZYX(joint_item.rotation.x, joint_item.rotation.y, joint_item.rotation.z);

        btTransform transform;
        transform.setIdentity();
        transform.setOrigin(btVector3(joint_item.position.x, joint_item.position.y, joint_item.position.z));
        transform.setBasis(rotation_matrix);

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

        btTransform inverse_a;
        btTransform inverse_b;
        inverse_a.setFromOpenGLMatrix(initial_transform + rigidbody_a_index * 16);
        inverse_b.setFromOpenGLMatrix(initial_transform + rigidbody_b_index * 16);
        inverse_a = inverse_a.inverse() * transform;
        inverse_b = inverse_b.inverse() * transform;

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

void PhysicsWorld::Step(float delta_time, int max_sub_steps, float fixed_time_step) {
    size_t rigidbody_index = 0;
    for (auto& rigidbody : this->rigidbodies) {
        rigidbody.motion_state->GetFromWorld(this, rigidbody_index++);
    }
    // this->world->stepSimulation(delta_time, max_sub_steps, fixed_time_step);
    rigidbody_index = 0;
    for (auto& rigidbody : this->rigidbodies) {
        if (!rigidbody.motion_state->IsDirty()) {
            continue;
        }
        rigidbody.motion_state->SetToWorld(this, rigidbody_index++);
    }
}

}  // namespace blazerod::physics
