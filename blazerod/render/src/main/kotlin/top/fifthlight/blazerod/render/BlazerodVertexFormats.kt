package top.fifthlight.blazerod.render

import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.irisshaders.iris.vertices.IrisVertexFormats
import net.minecraft.client.render.VertexFormats

object BlazerodVertexFormats {
    val POSITION: VertexFormat = VertexFormats.POSITION                   // 12 12

    val POSITION_COLOR_TEXTURE: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)                    // 12 12
        .add("Color", VertexFormatElement.COLOR)                          // 4  16
        .add("UV0", VertexFormatElement.UV0)                              // 8  24
        .padding(8)                                                       // 8  32
        .build()

    val POSITION_COLOR_TEXTURE_JOINT_WEIGHT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)                    // 12 12
        .add("Color", VertexFormatElement.COLOR)                          // 4  16
        .add("UV0", VertexFormatElement.UV0)                              // 8  24
        .add("Joint", BlazerodVertexFormatElements.JOINT)                 // 4  28
        .add("Weight", BlazerodVertexFormatElements.WEIGHT)               // 4  32
        .build()

    val POSITION_COLOR_TEXTURE_NORMAL: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)                    // 12 12
        .add("Color", VertexFormatElement.COLOR)                          // 4  16
        .add("UV0", VertexFormatElement.UV0)                              // 8  24
        .add("Normal", VertexFormatElement.NORMAL)                        // 3  27
        .padding(5)                                                       // 5  32
        .build()

    val POSITION_COLOR_TEXTURE_NORMAL_JOINT_WEIGHT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)                    // 12 12
        .add("Color", VertexFormatElement.COLOR)                          // 4  16
        .add("UV0", VertexFormatElement.UV0)                              // 8  24
        .add("Normal", VertexFormatElement.NORMAL)                        // 3  27
        .padding(1)                                                       // 1  28
        .add("Joint", BlazerodVertexFormatElements.JOINT)                 // 4  32
        .add("Weight", BlazerodVertexFormatElements.WEIGHT)               // 4  36
        .padding(12)                                                      // 12 48
        .build()

    val ENTITY_PADDED: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)                    // 12 12
        .add("Color", VertexFormatElement.COLOR)                          // 4  16
        .add("UV0", VertexFormatElement.UV0)                              // 8  24
        .add("UV1", VertexFormatElement.UV1)                              // 4  28
        .add("UV2", VertexFormatElement.UV2)                              // 4  32
        .add("Normal", VertexFormatElement.NORMAL)                        // 3  35
        .padding(13)                                                      // 13 48
        .build()

    val IRIS_ENTITY_PADDED: VertexFormat by lazy {
        VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)                // 12 12
            .add("Color", VertexFormatElement.COLOR)                      // 4  16
            .add("UV0", VertexFormatElement.UV0)                          // 8  24
            .add("UV1", VertexFormatElement.UV1)                          // 4  28
            .add("UV2", VertexFormatElement.UV2)                          // 4  32
            .add("Normal", VertexFormatElement.NORMAL)                    // 3  35
            .padding(1)                                                   // 1  36
            .add("iris_Entity", IrisVertexFormats.ENTITY_ID_ELEMENT)      // 6  42
            .padding(2)                                                   // 2  44
            .add("mc_midTexCoord", IrisVertexFormats.MID_TEXTURE_ELEMENT) // 8  52
            .add("at_tangent", IrisVertexFormats.TANGENT_ELEMENT)         // 4  56
            .padding(8)                                                   // 8  64
            .build()
    }
}