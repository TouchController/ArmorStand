package top.fifthlight.blazerod.model.uniform

import top.fifthlight.blazerod.layout.GpuDataLayout
import top.fifthlight.blazerod.layout.LayoutStrategy

object VanillaDataUniformBuffer : UniformBuffer<VanillaDataUniformBuffer, VanillaDataUniformBuffer.VanillaDataLayout>(
    name = "VanillaDataUniformBuffer",
) {
    override val layout: VanillaDataLayout
        get() = VanillaDataLayout

    object VanillaDataLayout : GpuDataLayout<VanillaDataLayout>() {
        override val strategy: LayoutStrategy
            get() = LayoutStrategy.Std140LayoutStrategy
        var baseColor by rgba()
    }
}