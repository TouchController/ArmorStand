#version 150

#moj_import <minecraft:fog.glsl>

uniform sampler2D SamplerBaseColor;

layout(std140) uniform VanillaData {
    vec4 BaseColor;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(SamplerBaseColor, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= vertexColor * BaseColor;
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif
    #ifndef EMISSIVE
    color *= lightMapColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
