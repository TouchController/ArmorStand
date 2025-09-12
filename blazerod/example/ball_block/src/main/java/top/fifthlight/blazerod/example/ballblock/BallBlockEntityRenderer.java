package top.fifthlight.blazerod.example.ballblock;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

// Shamelessly copied from https://docs.fabricmc.net/zh_cn/develop/blocks/block-entity-renderer
public class BallBlockEntityRenderer implements BlockEntityRenderer<BallBlockEntity> {
    public BallBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(BallBlockEntity entity, float tickProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        var instance = BallBlockMod.getBallInstance();
        if (instance == null) {
            return;
        }
        var client = MinecraftClient.getInstance();
        var frameBuffer = client.getFramebuffer();
        var colorFrameBuffer = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : frameBuffer.getColorAttachmentView();
        var depthFrameBuffer = frameBuffer.useDepthAttachment
                ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : frameBuffer.getDepthAttachmentView())
                : null;
        var renderer = BallBlockMod.getRenderer();
        renderer.render(Objects.requireNonNull(colorFrameBuffer), depthFrameBuffer, instance.createRenderTask(matrices.peek().getPositionMatrix(), light), instance.getScene());
    }
}
