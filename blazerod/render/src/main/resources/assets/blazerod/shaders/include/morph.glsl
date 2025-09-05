#blazerod_version version(<4.3) ? 150 : 430
#blazerod_extension version(<4.3) && defined(SUPPORT_SSBO); GL_ARB_shader_storage_buffer_object : require

#ifdef MORPHED

#ifndef MAX_ENABLED_MORPH_TARGETS
#error "MAX_ENABLED_MORPH_TARGETS not defined"
#endif

// Same for every instances
#ifdef SUPPORT_SSBO
layout(std430) buffer MorphPositionBlock {
    vec3 MorphPositionData[];
};
layout(std430) buffer MorphColorBlock {
    vec4 MorphColorData[];
};
layout(std430) buffer MorphTexCoordBlock {
    vec2 MorphTexCoordData[];
};
#else// SUPPORT_SSBO
uniform samplerBuffer MorphPositionData;
uniform samplerBuffer MorphColorData;
uniform samplerBuffer MorphTexCoordData;
#endif// SUPPORT_SSBO

// for all targets (include not-enabled, so every instance is same)
layout(std140) uniform MorphData {
    int TotalVertices;
    int PosTargets;
    int ColorTargets;
    int TexCoordTargets;
    int TotalTargets;// Sum of PosTargets, ColorTargets, TexCoordTargets
};

// index for all targets (include not-enabled)
#ifdef SUPPORT_SSBO
layout(std430) buffer MorphWeightsData {
    float MorphWeights[];
};
#else// SUPPORT_SSBO
uniform samplerBuffer MorphWeights;
#endif// SUPPORT_SSBO

struct index_t {
    int pos;
    int color;
    int texCoord;
};

#ifdef SUPPORT_SSBO
struct morph_item_t {
    int posCount;
    int colorCount;
    int texCoordCount;
    index_t indices[MAX_ENABLED_MORPH_TARGETS];
};

layout(std430) buffer MorphTargetIndicesData {
    morph_item_t MorphTargetIndices[];
};
#else// SUPPORT_SSBO
uniform isamplerBuffer MorphTargetIndices;
#endif// SUPPORT_SSBO

#ifndef INSTANCE_SIZE
#error "INSTANCE_SIZE not defined"
#endif// INSTANCE_SIZE
#ifdef INSTANCED
#define MORPH_INSTANCE_ID gl_InstanceID
#else// INSTANCED
#define MORPH_INSTANCE_ID 0
#endif// INSTANCED

#ifdef COMPUTE_SHADER
#define MORPH_VERTEX_ID int(gl_GlobalInvocationID.x)
#else
#define MORPH_VERTEX_ID gl_VertexID
#endif

#ifdef SUPPORT_SSBO
// @formatter:off
#define MACRO_MORPH_FUNCTION(RETURN_TYPE, FUNCTION_NAME, BASE_VAR_TYPE, BASE_VAR_ACCESSOR, MORPH_DATA_BUFFER_VAR, OFFSET, COUNT_NAME, INDEX_NAME, WEIGHT_OFFSET) \
RETURN_TYPE FUNCTION_NAME(BASE_VAR_TYPE baseVar) {                                                                                                               \
    RETURN_TYPE delta = RETURN_TYPE(0.0);                                                                                                                        \
    int modelIndex = MORPH_INSTANCE_ID;                                                                                                                          \
    int targetSize = MorphTargetIndices[modelIndex].COUNT_NAME;                                                                                                  \
                                                                                                                                                                 \
    for (int i = 0; i < targetSize; i++) {                                                                                                                       \
        int targetIndex = MorphTargetIndices[modelIndex].indices[i].INDEX_NAME;                                                                                  \
        float weight = MorphWeights[modelIndex * TotalTargets + WEIGHT_OFFSET + targetIndex];                                                                    \
        delta += MORPH_DATA_BUFFER_VAR[MORPH_VERTEX_ID + targetIndex * TotalVertices] * weight;                                                                  \
    }                                                                                                                                                            \
    return baseVar + delta;                                                                                                                                      \
}
// @formatter:on
#else// SUPPORT_SSBO
#define MODEL_ITEM_SIZE (3 + 3 * (MAX_ENABLED_MORPH_TARGETS))
// @formatter:off
#define MACRO_MORPH_FUNCTION(RETURN_TYPE, FUNCTION_NAME, BASE_VAR_TYPE, BASE_VAR_ACCESSOR, MORPH_DATA_BUFFER_VAR, OFFSET, COUNT_NAME, INDEX_NAME, WEIGHT_OFFSET) \
RETURN_TYPE FUNCTION_NAME(BASE_VAR_TYPE baseVar) {                                                                                                               \
    RETURN_TYPE delta = RETURN_TYPE(0.0);                                                                                                                        \
    int modelIndex = MORPH_INSTANCE_ID;                                                                                                                          \
    int baseModelItemOffset = modelIndex * MODEL_ITEM_SIZE;                                                                                                      \
    int targetSize = texelFetch(MorphTargetIndices, baseModelItemOffset + OFFSET).x;                                                                             \
    for (int i = 0; i < MAX_ENABLED_MORPH_TARGETS; i++) {                                                                                                        \
        if (i >= targetSize) {                                                                                                                                   \
            break;                                                                                                                                               \
        }                                                                                                                                                        \
        int targetIndex = texelFetch(MorphTargetIndices, baseModelItemOffset + 3 + 3 * i + OFFSET).x;                                                            \
        float weight = texelFetch(MorphWeights, modelIndex * TotalTargets + WEIGHT_OFFSET + targetIndex).x;                                                      \
        delta += texelFetch(MORPH_DATA_BUFFER_VAR, MORPH_VERTEX_ID + targetIndex * TotalVertices).BASE_VAR_ACCESSOR * weight;                                    \
    }                                                                                                                                                            \
    return baseVar + delta;                                                                                                                                      \
}
// @formatter:on
#endif// SUPPORT_SSBO

MACRO_MORPH_FUNCTION(vec3, applyPositionMorph, vec3, xyz,  MorphPositionData, 0, posCount,      pos,      0)
MACRO_MORPH_FUNCTION(vec4, applyColorMorph,    vec4, rgba, MorphColorData,    1, colorCount,    color,    PosTargets)
MACRO_MORPH_FUNCTION(vec2, applyTexCoordMorph, vec2, st,   MorphTexCoordData, 2, texCoordCount, texCoord, PosTargets + ColorTargets)

#define GET_MORPHED_VERTEX_POSITION(position) applyPositionMorph(position)
#define GET_MORPHED_VERTEX_COLOR(color) applyColorMorph(color)
#define GET_MORPHED_VERTEX_TEX_COORD(texCoord) applyTexCoordMorph(texCoord)

#else// MORPHED

#define GET_MORPHED_VERTEX_POSITION(position) (position)
#define GET_MORPHED_VERTEX_COLOR(color) (color)
#define GET_MORPHED_VERTEX_TEX_COORD(texCoord) (texCoord)

#endif// MORPHED