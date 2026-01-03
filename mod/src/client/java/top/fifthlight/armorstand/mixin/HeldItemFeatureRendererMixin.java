package top.fifthlight.armorstand.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fifthlight.armorstand.extension.internal.PlayerEntityRenderStateExtInternal;
import top.fifthlight.armorstand.state.ModelInstanceManager;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemFeatureRendererMixin {
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void armorstand$cancelVanillaHeldItemRender(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        ArmedEntityRenderState state,
        float limbAngle,
        float limbDistance,
        CallbackInfo ci
    ) {
        if (!(state instanceof PlayerEntityRenderState)) {
            return;
        }

        var uuid = ((PlayerEntityRenderStateExtInternal) state).armorstand$getUuid();
        if (uuid == null) {
            return;
        }

        var entry = ModelInstanceManager.INSTANCE.get(uuid, System.nanoTime(), false);
        if (entry instanceof ModelInstanceManager.ModelInstanceItem.Model) {
            ci.cancel();
        }
    }
}
