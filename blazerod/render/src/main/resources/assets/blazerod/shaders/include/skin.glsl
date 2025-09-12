#blazerod_version version(<4.3) ? 150 : 430
#blazerod_extension version(<4.3) && defined(SUPPORT_SSBO); GL_ARB_shader_storage_buffer_object : require

#ifdef SKINNED
#moj_import <blazerod:joint.glsl>
in ivec4 Joint;
in vec4 Weight;

#ifndef INSTANCE_SIZE
#error "INSTANCE_SIZE not defined"
#endif // INSTANCE_SIZE
#ifdef INSTANCED
#define SKIN_INSTANCE_ID gl_InstanceID
#else // INSTANCED
#define SKIN_INSTANCE_ID 0
#endif // INSTANCED

layout(std140) uniform SkinModelIndices {
    int skinJoints;
};

#define GET_SKINNED_VERTEX_POSITION(position) (skinPositionTransform(position, Weight, Joint + ivec4(skinJoints * SKIN_INSTANCE_ID)))
#define GET_SKINNED_VERTEX_NORMAL(normal) (skinNormalTransform(normal, Weight, Joint + ivec4(skinJoints * SKIN_INSTANCE_ID)))

#else // SKINNED

#define GET_SKINNED_VERTEX_POSITION(position) (position)
#define GET_SKINNED_VERTEX_NORMAL(normal) (normal)

#endif // SKINNED