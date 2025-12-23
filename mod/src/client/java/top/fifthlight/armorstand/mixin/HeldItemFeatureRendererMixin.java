package top.fifthlight.armorstand.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fifthlight.armorstand.config.ConfigHolder;
import top.fifthlight.armorstand.extension.internal.PlayerEntityRenderStateExtInternal;
import top.fifthlight.armorstand.state.ModelInstanceManager;
import top.fifthlight.blazerod.model.HumanoidTag;
import top.fifthlight.blazerod.model.NodeTransformView;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemFeatureRendererMixin {
    private static final Matrix4f ARMORSTAND$HAND_WORLD_MATRIX = new Matrix4f();
    private static final Matrix4f ARMORSTAND$ITEM_LOCAL_MATRIX = new Matrix4f();

    @Redirect(
        method = "renderItem(Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/ModelWithArms;setArmAngle(Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V"
        )
    )
    private void armorstand$redirectSetArmAngle(
        ModelWithArms model,
        Arm arm,
        MatrixStack matrices,
        ArmedEntityRenderState state,
        ItemRenderState itemState,
        Arm renderArm,
        MatrixStack renderMatrices,
        VertexConsumerProvider consumers,
        int light
    ) {
        if (!(state instanceof PlayerEntityRenderState)) {
            model.setArmAngle(arm, matrices);
            return;
        }

        var uuid = ((PlayerEntityRenderStateExtInternal) state).armorstand$getUuid();
        if (uuid == null) {
            model.setArmAngle(arm, matrices);
            return;
        }

        var entry = ModelInstanceManager.INSTANCE.get(uuid, System.nanoTime(), true);
        if (!(entry instanceof ModelInstanceManager.ModelInstanceItem.Model modelItem)) {
            model.setArmAngle(arm, matrices);
            return;
        }

        var instance = modelItem.getInstance();
        var scene = instance.getScene();

        var tag = arm == Arm.RIGHT ? HumanoidTag.RIGHT_HAND : HumanoidTag.LEFT_HAND;
        var node = scene.getHumanoidTagMap().get(tag);
        if (node == null) {
            model.setArmAngle(arm, matrices);
            return;
        }

        instance.copyNodeWorldTransform(node.getNodeIndex(), ARMORSTAND$HAND_WORLD_MATRIX);

        ARMORSTAND$ITEM_LOCAL_MATRIX.identity();
        ARMORSTAND$ITEM_LOCAL_MATRIX.scale(ConfigHolder.INSTANCE.getConfig().getValue().getModelScale());

        NodeTransformView renderTransform = scene.getRenderTransform();
        if (renderTransform != null) {
            renderTransform.applyOnMatrix(ARMORSTAND$ITEM_LOCAL_MATRIX);
        }

        ARMORSTAND$ITEM_LOCAL_MATRIX.mul(ARMORSTAND$HAND_WORLD_MATRIX);

        matrices.multiplyPositionMatrix(ARMORSTAND$ITEM_LOCAL_MATRIX);
    }
}
