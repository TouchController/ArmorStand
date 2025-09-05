package top.fifthlight.blazerod.extension

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.GpuDevice
import java.nio.ByteBuffer
import java.util.function.Supplier

fun GpuDevice.createBuffer(
    labelGetter: Supplier<String>?,
    usage: Int,
    extraUsage: Int,
    size: Int,
): GpuBuffer = (this as GpuDeviceExt).`blazerod$createBuffer`(labelGetter, usage, extraUsage, size)

fun GpuDevice.createBuffer(
    labelGetter: Supplier<String>?,
    usage: Int,
    extraUsage: Int,
    data: ByteBuffer,
): GpuBuffer = (this as GpuDeviceExt).`blazerod$createBuffer`(labelGetter, usage, extraUsage, data)

val GpuDevice.supportTextureBufferSlice: Boolean
    get() = (this as GpuDeviceExt).`blazerod$supportTextureBufferSlice`()

val GpuDevice.supportSsbo: Boolean
    get() = (this as GpuDeviceExt).`blazerod$supportSsbo`()

val GpuDevice.supportComputeShader: Boolean
    get() = (this as GpuDeviceExt).`blazerod$supportComputeShader`()

val GpuDevice.supportMemoryBarrier: Boolean
    get() = (this as GpuDeviceExt).`blazerod$supportMemoryBarrier`()

val GpuDevice.supportShaderPacking: Boolean
    get() = (this as GpuDeviceExt).`blazerod$supportShaderPacking`()

val GpuDevice.maxSsboBindings: Int
    get() = (this as GpuDeviceExt).`blazerod$getMaxSsboBindings`()

val GpuDevice.maxSsboInVertexShader: Int
    get() = (this as GpuDeviceExt).`blazerod$getMaxSsboInVertexShader`()

val GpuDevice.maxSsboInFragmentShader: Int
    get() = (this as GpuDeviceExt).`blazerod$getMaxSsboInFragmentShader`()

val GpuDevice.ssboOffsetAlignment: Int
    get() = (this as GpuDeviceExt).`blazerod$getSsboOffsetAlignment`()

val GpuDevice.textureBufferOffsetAlignment: Int
    get() = (this as GpuDeviceExt).`blazerod$getTextureBufferOffsetAlignment`()
