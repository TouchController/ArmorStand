#include <jni.h>

#include "PhysicsScene.h"
#include "PhysicsWorld.h"
#include "top_fifthlight_blazerod_physics_PhysicsLibrary.h"

using blazerod::physics::PhysicsScene;
using blazerod::physics::PhysicsWorld;

extern "C" {
/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    createPhysicsScene
 * Signature: (Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)J
 */
JNIEXPORT jlong JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_createPhysicsScene(JNIEnv* env,
                                                                                               jclass clazz,
                                                                                               jobject rigidbodies,
                                                                                               jobject joints) {
    if (rigidbodies == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/NullPointerException"), "Rigidbodies cannot be null");
        return 0;
    }
    if (joints == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/NullPointerException"), "Joints cannot be null");
        return 0;
    }

    size_t rigidbodies_size = env->GetDirectBufferCapacity(rigidbodies);
    if (rigidbodies_size == -1) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Rigidbodies buffer is not direct buffer");
        return 0;
    }
    void* rigidbodies_ptr = env->GetDirectBufferAddress(rigidbodies);

    size_t joints_size = env->GetDirectBufferCapacity(joints);
    if (joints_size == -1) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Joints buffer is not direct buffer");
        return 0;
    }
    void* joints_ptr = env->GetDirectBufferAddress(joints);

    try {
        auto physics_scene_ptr = new PhysicsScene(rigidbodies_size, rigidbodies_ptr, joints_size, joints_ptr);
        return reinterpret_cast<jlong>(physics_scene_ptr);
    } catch (std::exception& e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), e.what());
        return 0;
    }
}

/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    destroyPhysicsScene
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_destroyPhysicsScene(JNIEnv* env,
                                                                                               jclass clazz,
                                                                                               jlong physics_scene) {
    delete reinterpret_cast<PhysicsScene*>(physics_scene);
}

/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    createPhysicsWorld
 * Signature: (JLjava/nio/ByteBuffer;)J
 */
JNIEXPORT jlong JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_createPhysicsWorld(
    JNIEnv* env, jclass clazz, jlong physics_scene, jobject initial_transform) {
    size_t initial_transform_size = env->GetDirectBufferCapacity(initial_transform);
    if (initial_transform_size == -1) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Initial transform buffer is not direct buffer");
        return 0;
    } else if (initial_transform_size % 4 != 0) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Initial transform buffer size is not a multiple of 4");
    }
    void* initial_transform_ptr = env->GetDirectBufferAddress(initial_transform);
    size_t initial_transform_count = initial_transform_size / 4;

    try {
        auto physics_world_ptr =
            new PhysicsWorld(*reinterpret_cast<PhysicsScene*>(physics_scene), initial_transform_count,
                             reinterpret_cast<float*>(initial_transform_ptr));
        return reinterpret_cast<jlong>(physics_world_ptr);
    } catch (std::exception& e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), e.what());
        return 0;
    }
}

/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    getTransformBuffer
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_getTransformBuffer(JNIEnv* env,
                                                                                                 jclass clazz,
                                                                                                 jlong physics_world) {
    auto physics_world_ptr = reinterpret_cast<PhysicsWorld*>(physics_world);
    auto transform_buffer = physics_world_ptr->GetTransformBuffer();
    return env->NewDirectByteBuffer(transform_buffer.data(), transform_buffer.size() * sizeof(float));
}

/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    stepPhysicsWorld
 * Signature: (JFFF)V
 */
JNIEXPORT void JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_stepPhysicsWorld(
    JNIEnv* env, jclass clazz, jlong physics_world, jfloat delta_time, jfloat max_sub_steps, jfloat fixed_time_step) {
    auto physics_world_ptr = reinterpret_cast<PhysicsWorld*>(physics_world);
    physics_world_ptr->Step(delta_time, max_sub_steps, fixed_time_step);
}

/*
 * Class:     top_fifthlight_blazerod_physics_PhysicsLibrary
 * Method:    destroyPhysicsWorld
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_top_fifthlight_blazerod_physics_PhysicsLibrary_destroyPhysicsWorld(JNIEnv* env,
                                                                                               jclass clazz,
                                                                                               jlong physics_world) {
    delete reinterpret_cast<PhysicsWorld*>(physics_world);
}
}
