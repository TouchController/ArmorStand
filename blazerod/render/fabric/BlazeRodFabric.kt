package top.fifthlight.blazerod

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPassImpl
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.api.event.RenderEvents
import top.fifthlight.blazerod.debug.*
import top.fifthlight.blazerod.physics.PhysicsInterface
import top.fifthlight.blazerod.runtime.resource.RenderTexture
import top.fifthlight.blazerod.runtime.uniform.UniformBuffer
import top.fifthlight.blazerod.util.dispatchers.ThreadExecutorDispatcher
import top.fifthlight.blazerod.util.objectpool.cleanupObjectPools
import javax.swing.SwingUtilities

object BlazeRodFabric : ClientModInitializer {
    private val LOGGER = LoggerFactory.getLogger(BlazeRodFabric::class.java)

    override fun onInitializeClient() {
        BlazeRod.mainDispatcher = ThreadExecutorDispatcher(MinecraftClient.getInstance())

        PhysicsInterface.load()

        if (System.getProperty("blazerod.debug") == "true") {
            BlazeRod.debug = true
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