package top.fifthlight.blazerod.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fifthlight.blazerod.extension.ShaderTypeExt;

@Mixin(ShaderType.class)
public class ShaderTypeMixin {
    @Shadow
    @Final
    @Mutable
    private static ShaderType[] $VALUES;

    @Shadow
    @Final
    @Mutable
    private static ShaderType[] TYPES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onInitialize(CallbackInfo ci) {
        var newValues = new ShaderType[$VALUES.length + 1];
        var nextOrdinal = $VALUES.length;
        System.arraycopy($VALUES, 0, newValues, 0, $VALUES.length);

        var COMPUTE = blazerod$invokeInit("COMPUTE", nextOrdinal, "compute", ".csh");
        ShaderTypeExt.COMPUTE = COMPUTE;
        newValues[nextOrdinal] = COMPUTE;

        $VALUES = newValues;
        TYPES = newValues;
    }

    @Invoker("<init>")
    public static ShaderType blazerod$invokeInit(String enumName, int ordinal, String name, String extension) {
        throw new AssertionError();
    }
}
