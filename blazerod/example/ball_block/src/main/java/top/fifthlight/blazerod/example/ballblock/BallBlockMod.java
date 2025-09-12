package top.fifthlight.blazerod.example.ballblock;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import top.fifthlight.blazerod.model.ModelFileLoaders;
import top.fifthlight.blazerod.model.ModelInstance;
import top.fifthlight.blazerod.model.RenderScene;
import top.fifthlight.blazerod.model.TransformId;
import top.fifthlight.blazerod.model.load.ModelLoader;
import top.fifthlight.blazerod.model.renderer.Renderer;
import top.fifthlight.blazerod.model.renderer.VertexShaderTransformRenderer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

public class BallBlockMod implements ClientModInitializer {
    public static final String MOD_ID = "ball_block";
    private static RenderScene BALL_SCENE = null;
    // Share same instance
    private static ModelInstance BALL_INSTANCE = null;
    private static Renderer<?, ?> RENDERER = null;

    private void loadModel() {
        var resource = getClass().getClassLoader().getResource("ball.glb");
        if (resource == null) {
            throw new IllegalStateException("No ball resource");
        }
        URI uri;
        try {
            uri = resource.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        // in case it not loaded
        if ("jar".equals(uri.getScheme())) {
            try {
                //noinspection resource
                FileSystems.newFileSystem(uri, Map.of("create", "true"));
            } catch (FileSystemAlreadyExistsException ignored) {} catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        var file = Path.of(uri);
        var result = ModelFileLoaders.INSTANCE.probeAndLoad(file);
        if (result == null) {
            throw new IllegalStateException("No model loader");
        }
        var model = result.getModel();
        if (model == null) {
            throw new IllegalStateException("Ball model don't contain model");
        }
        RENDERER = VertexShaderTransformRenderer.create();
        ModelLoader.loadModelAsFuture(model).thenAccept(scene -> {
            if (scene == null) {
                throw new IllegalStateException("Ball model load failed");
            }
            BALL_SCENE = scene;
            BALL_SCENE.increaseReferenceCount();
            BALL_INSTANCE = new ModelInstance(BALL_SCENE);
            BALL_INSTANCE.increaseReferenceCount();
            var rootTransformNodeIndex = BALL_SCENE.getRootNode().getNodeIndex();
            // Move and scale
            BALL_INSTANCE.setTransformDecomposed(rootTransformNodeIndex, TransformId.ABSOLUTE, matrix -> {
                matrix.getScale().mul(0.5f);
                matrix.getTranslation().add(0.5f, 0.5f, 0.5f);
            });
            // No physics applied
            BALL_INSTANCE.updateRenderData(0f);
        });
    }

    public static ModelInstance getBallInstance() {
        return BALL_INSTANCE;
    }

    public static Renderer<?, ?> getRenderer() {
        return RENDERER;
    }

    @Override
    public void onInitializeClient() {
        ModBlocks.initialize();
        ModelFileLoaders.initialize();
        BlockEntityRendererFactories.register(ModBlockEntities.BALL_BLOCK_ENTITY, BallBlockEntityRenderer::new);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> loadModel());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            BALL_INSTANCE.decreaseReferenceCount();
            BALL_SCENE.decreaseReferenceCount();
        });
    }
}
