#blazerod_version version(<4.3) ? 150 : 430
#blazerod_extension version(<4.3) && defined(SUPPORT_SSBO); GL_ARB_shader_storage_buffer_object : require

#ifdef SUPPORT_SSBO
layout(std430) buffer JointsData {
    mat4 Joints[];
};
#else// SUPPORT_SSBO
uniform samplerBuffer Joints;
#endif// SUPPORT_SSBO

mat4 getJointPositionMatrix(int index) {
    #ifdef SUPPORT_SSBO
    return Joints[index * 2];
    #else// SUPPORT_SSBO
    int base = index * 8;
    return mat4(
    texelFetch(Joints, base),
    texelFetch(Joints, base + 1),
    texelFetch(Joints, base + 2),
    texelFetch(Joints, base + 3)
    );
    #endif// SUPPORT_SSBO
}

mat3 getJointNormalMatrix(int index) {
    #ifdef SUPPORT_SSBO
    return mat3(Joints[index * 2 + 1]);
    #else// SUPPORT_SSBO
    int base = index * 8 + 4;
    return mat3(
    texelFetch(Joints, base).xyz,
    texelFetch(Joints, base + 1).xyz,
    texelFetch(Joints, base + 2).xyz
    );
    #endif// SUPPORT_SSBO
}

vec4 skinPositionTransform(vec4 position, vec4 weight, ivec4 joint_indices) {
    if (weight == vec4(0.0)) {
        return position;
    }
    vec4 posX = getJointPositionMatrix(joint_indices.x) * position;
    vec4 posY = getJointPositionMatrix(joint_indices.y) * position;
    vec4 posZ = getJointPositionMatrix(joint_indices.z) * position;
    vec4 posW = getJointPositionMatrix(joint_indices.w) * position;
    return posX * weight.x + posY * weight.y + posZ * weight.z + posW * weight.w;
}

vec3 skinNormalTransform(vec3 normal, vec4 weight, ivec4 joint_indices) {
    if (weight == vec4(0.0)) {
        return normal;
    }
    vec3 posX = getJointNormalMatrix(joint_indices.x) * normal;
    vec3 posY = getJointNormalMatrix(joint_indices.y) * normal;
    vec3 posZ = getJointNormalMatrix(joint_indices.z) * normal;
    vec3 posW = getJointNormalMatrix(joint_indices.w) * normal;
    return posX * weight.x + posY * weight.y + posZ * weight.z + posW * weight.w;
}
