package top.fifthlight.blazerod

import com.mojang.logging.LogUtils
import kotlinx.coroutines.CoroutineDispatcher
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPassImpl
import top.fifthlight.armorstand.debug.ResourceCountTrackerFrame
import top.fifthlight.blazerod.debug.*
import top.fifthlight.blazerod.event.RenderEvents
import top.fifthlight.blazerod.model.resource.RenderTexture
import top.fifthlight.blazerod.model.uniform.UniformBuffer
import top.fifthlight.blazerod.physics.PhysicsLibrary
import top.fifthlight.blazerod.util.ThreadExecutorDispatcher
import top.fifthlight.blazerod.util.cleanupObjectPools
import javax.swing.SwingUtilities

object BlazeRod: ClientModInitializer {
    private val LOGGER = LogUtils.getLogger()

    const val INSTANCE_SIZE = 128
    const val MAX_ENABLED_MORPH_TARGETS = 32
    const val COMPUTE_LOCAL_SIZE = 64

    lateinit var mainDispatcher: CoroutineDispatcher
    var debug = false

    override fun onInitializeClient() {
        mainDispatcher = ThreadExecutorDispatcher(MinecraftClient.getInstance())

        if (System.getProperty("blazerod.debug") == "true") {
            debug = true
            RenderPassImpl.IS_DEVELOPMENT = true
            if (System.getProperty("blazerod.debug.gui") == "true") {
                ResourceCountTracker.initialize()
                ObjectPoolTracker.initialize()
                UniformBufferTracker.initialize()
                System.setProperty("java.awt.headless", "false")
                SwingUtilities.invokeLater {
                    try {
                        ResourceCountTrackerFrame().isVisible = true
                        ObjectCountTrackerFrame().isVisible = true
                        UniformBufferTrackerFrame().isVisible = true
                    } catch (ex: Exception) {
                        LOGGER.info("Failed to show debug windows", ex)
                    }
                }
            }
        }

        if (System.getProperty("blazerod.no_physics") != "true") {
            PhysicsLibrary.init()
        }

        RenderEvents.INITIALIZE_DEVICE.register {
            // Trigger its loading in render thread
            RenderTexture.WHITE_RGBA_TEXTURE
        }

        RenderEvents.FLIP_FRAME.register {
            UniformBuffer.clear()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { client ->
            cleanupObjectPools()
            UniformBuffer.close()
        }
    }
}