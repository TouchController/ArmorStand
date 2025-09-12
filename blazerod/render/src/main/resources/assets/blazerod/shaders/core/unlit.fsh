#version 150

#moj_import <minecraft:fog.glsl>

uniform sampler2D SamplerBaseColor;

layout(std140) uniform UnlitData {
    vec4 BaseColor;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(SamplerBaseColor, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= vertexColor * BaseColor;
    color *= lightMapColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
