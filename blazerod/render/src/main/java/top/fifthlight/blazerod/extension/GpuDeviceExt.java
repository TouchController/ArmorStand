package top.fifthlight.blazerod.extension;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface GpuDeviceExt {
    @NotNull
    GpuBuffer blazerod$createBuffer(@Nullable Supplier<String> labelSupplier, int usage, int extraUsage, int size);

    @NotNull
    GpuBuffer blazerod$createBuffer(@Nullable Supplier<String> labelSupplier, int usage, int extraUsage, ByteBuffer data);

    boolean blazerod$supportTextureBufferSlice();
    boolean blazerod$supportSsbo();
    boolean blazerod$supportComputeShader();
    boolean blazerod$supportMemoryBarrier();

    boolean blazerod$supportShaderPacking();

    int blazerod$getMaxSsboBindings();
    int blazerod$getMaxSsboInVertexShader();
    int blazerod$getMaxSsboInFragmentShader();

    int blazerod$getSsboOffsetAlignment();
    int blazerod$getTextureBufferOffsetAlignment();
}
