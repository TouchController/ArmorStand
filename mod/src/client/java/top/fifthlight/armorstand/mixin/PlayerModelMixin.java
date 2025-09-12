package top.fifthlight.armorstand.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import top.fifthlight.armorstand.PlayerRenderer;
import top.fifthlight.armorstand.extension.internal.PlayerEntityRenderStateExtInternal;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerModelMixin {
    @Shadow
    public static int getOverlay(LivingEntityRenderState state, float whiteOverlayProgress) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract float getAnimationCounter(LivingEntityRenderState state);

    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;"))
    @Nullable
    public <T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
    RenderLayer render(LivingEntityRenderer<T, S, M> instance,
                       S state,
                       boolean showBody,
                       boolean translucent,
                       boolean showOutline,
                       Operation<RenderLayer> original,
                       S livingEntityRenderState,
                       MatrixStack matrixStack,
                       VertexConsumerProvider vertexConsumerProvider,
                       int light
    ) {
        if (!(state instanceof PlayerEntityRenderState)) {
            return original.call(instance, state, showBody, translucent, showOutline);
        }
        var uuid = ((PlayerEntityRenderStateExtInternal) state).armorstand$getUuid();
        if (uuid == null) {
            return original.call(instance, state, showBody, translucent, showOutline);
        }
        var overlay = getOverlay(state, getAnimationCounter(livingEntityRenderState));
        if (!PlayerRenderer.appendPlayer(uuid, (PlayerEntityRenderState) state, matrixStack, vertexConsumerProvider, light, overlay)) {
            return original.call(instance, state, showBody, translucent, showOutline);
        }
        return null;
    }
}
