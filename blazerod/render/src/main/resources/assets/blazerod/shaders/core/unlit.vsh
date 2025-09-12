#version 150

#moj_import <blazerod:instance.glsl>
#moj_import <blazerod:skin.glsl>
#moj_import <blazerod:morph.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
// Texture UV
in vec2 UV0;

// Lightmap texture
uniform sampler2D SamplerLightMap;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;

void main() {
    instance_t instance = get_instance();

    vec3 position = GET_MORPHED_VERTEX_POSITION(Position);
    vec4 color = GET_MORPHED_VERTEX_COLOR(Color);
    vec2 texCoord = GET_MORPHED_VERTEX_TEX_COORD(UV0);

    mat4 model_view_proj_mat = ProjMat * ViewMatrix * instance.model_mat;
    vec4 vertex_position = model_view_proj_mat * GET_SKINNED_VERTEX_POSITION(vec4(position, 1.0));

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    gl_Position = vertex_position;
    vertexColor = color;
    lightMapColor = texelFetch(SamplerLightMap, instance.light_map_uv / 16, 0);
    texCoord0 = texCoord;
}
