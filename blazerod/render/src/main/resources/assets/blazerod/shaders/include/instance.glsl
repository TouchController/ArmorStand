#blazerod_version version(<4.3) ? 150 : 430
#blazerod_extension version(<4.3) && defined(SUPPORT_SSBO); GL_ARB_shader_storage_buffer_object : require

#ifndef INSTANCE_SIZE
#error "INSTANCE_SIZE not defined"
#endif // INSTANCE_SIZE
#ifdef INSTANCED
#define INSTANCE_ID gl_InstanceID
#else // INSTANCED
#define INSTANCE_ID 0
#endif // INSTANCED

// mat4 LocalMatrices[PrimitiveSize][INSTANCE_SIZE]
#ifdef SUPPORT_SSBO
layout(std430) buffer LocalMatricesData {
    mat4 LocalMatrices[];
};
#else// SUPPORT_SSBO
uniform samplerBuffer LocalMatrices;// TBO declaration
#endif// SUPPORT_SSBO

layout (std140) uniform InstanceData {
    int PrimitiveSize;
    int PrimitiveIndex;
    mat4 ViewMatrix;
    mat4 ModelMatrices[INSTANCE_SIZE];
    mat4 ModelNormalMatrices[INSTANCE_SIZE];
    ivec2 LightMapUvs[INSTANCE_SIZE];
    ivec2 OverlayUvs[INSTANCE_SIZE];
};

struct instance_t {
    mat4 model_mat;
    mat3 model_normal_matrix;
    ivec2 light_map_uv;
    ivec2 overlay_uv;
};

instance_t get_instance() {
    instance_t instance;
    int matricesOffset = INSTANCE_ID * PrimitiveSize + PrimitiveIndex;

    mat4 local_matrix =
    #ifdef SUPPORT_SSBO
    LocalMatrices[matricesOffset * 2];
    #else// SUPPORT_SSBO
    mat4(
    texelFetch(LocalMatrices, matricesOffset * 8 + 0),
    texelFetch(LocalMatrices, matricesOffset * 8 + 1),
    texelFetch(LocalMatrices, matricesOffset * 8 + 2),
    texelFetch(LocalMatrices, matricesOffset * 8 + 3)
    );
    #endif// SUPPORT_SSBO

    mat4 local_normal_matrix =
    #ifdef SUPPORT_SSBO
    LocalMatrices[matricesOffset * 2 + 1];
    #else// SUPPORT_SSBO
    mat4(
    texelFetch(LocalMatrices, matricesOffset * 8 + 4),
    texelFetch(LocalMatrices, matricesOffset * 8 + 5),
    texelFetch(LocalMatrices, matricesOffset * 8 + 6),
    texelFetch(LocalMatrices, matricesOffset * 8 + 7)
    );
    #endif// SUPPORT_SSBO

    instance.model_mat = ModelMatrices[INSTANCE_ID] * local_matrix;
    instance.model_normal_matrix = mat3(ModelNormalMatrices[INSTANCE_ID] * local_normal_matrix);
    instance.light_map_uv = LightMapUvs[INSTANCE_ID];
    instance.overlay_uv = OverlayUvs[INSTANCE_ID];
    return instance;
}