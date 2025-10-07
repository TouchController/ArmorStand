package top.fifthlight.blazerod

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPassImpl
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory
import top.fifthlight.blazerod.api.event.RenderEvents
import top.fifthlight.blazerod.debug.*
import top.fifthlight.blazerod.physics.PhysicsInterface
import top.fifthlight.blazerod.runtime.resource.RenderTexture
import top.fifthlight.blazerod.runtime.uniform.UniformBuffer
import top.fifthlight.blazerod.util.dispatchers.ThreadExecutorDispatcher
import top.fifthlight.blazerod.util.objectpool.cleanupObjectPools
import javax.swing.SwingUtilities

@Mod("blazerod_render")
@EventBusSubscriber(modid = "blazerod_render", value = [Dist.CLIENT])
class BlazeRodNeoForge(private val container: ModContainer) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BlazeRodNeoForge::class.java)

        @SubscribeEvent
        @JvmStatic
        fun onClientSetup(event: FMLClientSetupEvent) {
            BlazeRod.mainDispatcher = ThreadExecutorDispatcher(MinecraftClient.getInstance())

            PhysicsInterface.load()

            // NeoForge initialize device before us, so no RenderEvents.INITIALIZE_DEVICE here
            event.enqueueWork {
                // GO MAIN THREAD!
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

                RenderTexture.WHITE_RGBA_TEXTURE
            }

            RenderEvents.FLIP_FRAME.register {
                UniformBuffer.clear()
            }

            NeoForge.EVENT_BUS.register(object {
                @SubscribeEvent
                fun clientStop(event: ClientStoppingEvent) {
                    cleanupObjectPools()
                    UniformBuffer.close()
                }
            })
        }
    }
}
