package top.fifthlight.blazerod.mixin.gl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.gl.*;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fifthlight.blazerod.extension.ShaderTypeExt;
import top.fifthlight.blazerod.extension.internal.GpuBufferExtInternal;
import top.fifthlight.blazerod.extension.internal.RenderPipelineExtInternal;
import top.fifthlight.blazerod.extension.internal.gl.GpuDeviceExtInternal;
import top.fifthlight.blazerod.extension.internal.gl.ShaderProgramExtInternal;
import top.fifthlight.blazerod.gl.CompiledComputePipeline;
import top.fifthlight.blazerod.pipeline.ComputePipeline;
import top.fifthlight.blazerod.render.gl.ShaderProgramExt;
import top.fifthlight.blazerod.util.GlslExtensionProcessor;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Mixin(GlBackend.class)
public abstract class GlBackendMixin implements GpuDeviceExtInternal {
    @Unique
    private static final boolean allowGlTextureBufferRange = true;
    @Unique
    private static final boolean allowGlShaderStorageBufferObject = true;
    @Unique
    private static final boolean allowSsboInVertexShader = true;
    @Unique
    private static final boolean allowSsboInFragmentShader = true;
    @Unique
    private static final boolean allowGlComputeShader = true;
    @Unique
    private static final boolean allowGlShaderImageLoadStore = true;
    @Unique
    private static final boolean allowGlShaderPacking = true;

    @Shadow
    @Final
    private Set<String> usedGlCapabilities;
    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    private BiFunction<Identifier, ShaderType, String> defaultShaderSourceGetter;
    @Shadow
    @Final
    private DebugLabelManager debugLabelManager;

    @Unique
    private final Map<ComputePipeline, CompiledComputePipeline> computePipelineCompileCache = new IdentityHashMap<>();
    @Unique
    private int glMajorVersion;
    @Unique
    private int glMinorVersion;

    @Unique
    private boolean supportTextureBufferSlice;
    @Unique
    private boolean supportSsbo;
    @Unique
    private boolean supportComputeShader;
    @Unique
    private boolean supportShaderImageLoadStore;
    @Unique
    private boolean supportShaderPacking;
    @Unique
    private int maxSsboBindings;
    @Unique
    private int maxSsboInVertexShader;
    @Unique
    private int maxSsboInFragmentShader;
    @Unique
    private int ssboOffsetAlignment;
    @Unique
    private int textureBufferOffsetAlignment;

    @Shadow
    public abstract GpuBuffer createBuffer(@Nullable Supplier<String> labelSupplier, int usage, int size);

    @Shadow
    public abstract GpuBuffer createBuffer(@Nullable Supplier<String> labelSupplier, int usage, ByteBuffer data);

    @Shadow
    protected abstract CompiledShader compileShader(Identifier id, ShaderType type, Defines defines, BiFunction<Identifier, ShaderType, String> sourceRetriever);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(long contextId, int debugVerbosity, boolean sync, BiFunction<Identifier, ShaderType, String> shaderSourceGetter, boolean renderDebugLabels, CallbackInfo ci, @Local(ordinal = 0) GLCapabilities glCapabilities) {
        if (allowGlTextureBufferRange && glCapabilities.GL_ARB_texture_buffer_range) {
            usedGlCapabilities.add("GL_ARB_texture_buffer_range");
            supportTextureBufferSlice = true;
        } else {
            supportTextureBufferSlice = false;
        }

        if (allowGlShaderStorageBufferObject
                && glCapabilities.GL_ARB_shader_storage_buffer_object
                && glCapabilities.GL_ARB_program_interface_query
                // OpenGL spec says GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS must be at least 8, but we check it
                // just in case some drivers don't follow the spec
                && GL11.glGetInteger(GL43C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) > 8) {
            usedGlCapabilities.add("GL_ARB_shader_storage_buffer_object");
            usedGlCapabilities.add("GL_ARB_program_interface_query");
            supportSsbo = true;
        } else {
            supportSsbo = false;
        }

        if (allowGlComputeShader && glCapabilities.GL_ARB_compute_shader) {
            usedGlCapabilities.add("GL_ARB_compute_shader");
            supportComputeShader = true;
        } else {
            supportComputeShader = false;
        }

        if (allowGlShaderImageLoadStore && glCapabilities.GL_ARB_shader_image_load_store) {
            usedGlCapabilities.add("GL_ARB_shader_image_load_store");
            supportShaderImageLoadStore = true;
        } else {
            supportShaderImageLoadStore = false;
        }

        if (allowGlShaderPacking && glCapabilities.GL_ARB_shading_language_packing) {
            usedGlCapabilities.add("GL_ARB_shading_language_packing");
            supportShaderPacking = true;
        } else {
            supportShaderPacking = false;
        }

        if (supportSsbo && allowSsboInVertexShader) {
            maxSsboInVertexShader = GL11.glGetInteger(GL43C.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS);
        } else {
            maxSsboInVertexShader = 0;
        }
        if (supportSsbo && allowSsboInFragmentShader) {
            maxSsboInFragmentShader = GL11.glGetInteger(GL43C.GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS);
        } else {
            maxSsboInFragmentShader = 0;
        }
        if (supportSsbo) {
            maxSsboBindings = GL11.glGetInteger(GL43C.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS);
        } else {
            maxSsboBindings = 0;
        }

        if (supportSsbo) {
            ssboOffsetAlignment = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
        } else {
            ssboOffsetAlignment = -1;
        }
        if (supportTextureBufferSlice) {
            textureBufferOffsetAlignment = GL11.glGetInteger(ARBTextureBufferRange.GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT);
        } else {
            textureBufferOffsetAlignment = -1;
        }

        glMajorVersion = GL11.glGetInteger(GL30C.GL_MAJOR_VERSION);
        glMinorVersion = GL11.glGetInteger(GL30C.GL_MINOR_VERSION);
    }

    @Inject(method = "compileRenderPipeline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderProgram;set(Ljava/util/List;Ljava/util/List;)V"))
    private void onSetShaderProgram(RenderPipeline pipeline, BiFunction<Identifier, ShaderType, String> sourceRetriever, CallbackInfoReturnable<CompiledShaderPipeline> cir, @Local ShaderProgram shaderProgram) {
        var shaderProgramExt = (ShaderProgramExtInternal) shaderProgram;
        var pipelineExt = (RenderPipelineExtInternal) pipeline;
        shaderProgramExt.blazerod$setStorageBuffers(pipelineExt.blazerod$getStorageBuffers());
    }

    @WrapOperation(method = "compileShader(Lnet/minecraft/client/gl/GlBackend$ShaderKey;Ljava/util/function/BiFunction;)Lnet/minecraft/client/gl/CompiledShader;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlImportProcessor;addDefines(Ljava/lang/String;Lnet/minecraft/client/gl/Defines;)Ljava/lang/String;"))
    public String modifyShaderSource(String source, Defines defines, Operation<String> original) {
        var context = new GlslExtensionProcessor.Context(glMajorVersion, glMinorVersion, defines);
        var processedShader = GlslExtensionProcessor.process(context, source);
        return original.call(processedShader, defines);
    }

    @NotNull
    @Override
    public GpuBuffer blazerod$createBuffer(@Nullable Supplier<String> labelSupplier, int usage, int extraUsage, int size) {
        var buffer = createBuffer(labelSupplier, usage, size);
        ((GpuBufferExtInternal) buffer).blazerod$setExtraUsage(extraUsage);
        return buffer;
    }

    @NotNull
    @Override
    public GpuBuffer blazerod$createBuffer(@Nullable Supplier<String> labelSupplier, int usage, int extraUsage, ByteBuffer data) {
        var buffer = createBuffer(labelSupplier, usage, data);
        ((GpuBufferExtInternal) buffer).blazerod$setExtraUsage(extraUsage);
        return buffer;
    }

    @Override
    public boolean blazerod$supportTextureBufferSlice() {
        return supportTextureBufferSlice;
    }

    @Override
    public boolean blazerod$supportSsbo() {
        return supportSsbo;
    }

    @Override
    public boolean blazerod$supportComputeShader() {
        return supportComputeShader;
    }

    @Override
    public boolean blazerod$supportMemoryBarrier() {
        // glMemoryBarrier is defined in ARB_shader_image_load_store
        return supportShaderImageLoadStore;
    }

    @Override
    public boolean blazerod$supportShaderPacking() {
        return supportShaderPacking;
    }

    @Override
    public int blazerod$getMaxSsboBindings() {
        if (!supportSsbo) {
            throw new IllegalStateException("SSBO is not supported");
        }
        return maxSsboBindings;
    }

    @Override
    public int blazerod$getMaxSsboInVertexShader() {
        if (!supportSsbo) {
            throw new IllegalStateException("SSBO is not supported");
        }
        return maxSsboInVertexShader;
    }

    @Override
    public int blazerod$getMaxSsboInFragmentShader() {
        if (!supportSsbo) {
            throw new IllegalStateException("SSBO is not supported");
        }
        return maxSsboInFragmentShader;
    }

    @Override
    public int blazerod$getSsboOffsetAlignment() {
        if (!supportSsbo) {
            throw new IllegalStateException("SSBO is not supported");
        }
        return ssboOffsetAlignment;
    }

    @Override
    public int blazerod$getTextureBufferOffsetAlignment() {
        if (!supportTextureBufferSlice) {
            throw new IllegalStateException("Texture buffer slice is not supported");
        }
        return textureBufferOffsetAlignment;
    }

    @Override
    public CompiledComputePipeline blazerod$compilePipelineCached(ComputePipeline pipeline) {
        return this.computePipelineCompileCache.computeIfAbsent(pipeline, p -> this.compileComputePipeline(pipeline, defaultShaderSourceGetter));
    }

    @Unique
    private CompiledComputePipeline compileComputePipeline(ComputePipeline pipeline, BiFunction<Identifier, ShaderType, String> sourceRetriever) {
        var compiledShader = compileShader(pipeline.getComputeShader(), ShaderTypeExt.COMPUTE, pipeline.getShaderDefines(), sourceRetriever);
        if (compiledShader == CompiledShader.INVALID_SHADER) {
            LOGGER.error("Couldn't compile pipeline {}: compute shader {} was invalid", pipeline.getLocation(), pipeline.getComputeShader());
            return new CompiledComputePipeline(pipeline, ShaderProgram.INVALID);
        } else {
            ShaderProgram shaderProgram;
            try {
                shaderProgram = ShaderProgramExt.create(compiledShader, pipeline.getLocation().toString());
            } catch (ShaderLoader.LoadException ex) {
                LOGGER.error("Couldn't compile program for pipeline {}: {}", pipeline.getLocation(), ex);
                return new CompiledComputePipeline(pipeline, ShaderProgram.INVALID);
            }

            shaderProgram.set(pipeline.getUniforms(), pipeline.getSamplers());
            ((ShaderProgramExtInternal) shaderProgram).blazerod$setStorageBuffers(pipeline.getStorageBuffers());
            debugLabelManager.labelShaderProgram(shaderProgram);
            return new CompiledComputePipeline(pipeline, shaderProgram);
        }
    }
}
