package top.fifthlight.armorstand

import com.mojang.logging.LogUtils
import kotlinx.coroutines.*
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.debug.ModelManagerDebugFrame
import top.fifthlight.armorstand.event.ScreenEvents
import top.fifthlight.armorstand.manage.ModelManagerHolder
import top.fifthlight.armorstand.network.PlayerModelUpdateS2CPayload
import top.fifthlight.armorstand.state.ClientModelPathManager
import top.fifthlight.armorstand.state.ModelHashManager
import top.fifthlight.armorstand.state.ModelInstanceManager
import top.fifthlight.armorstand.state.NetworkModelSyncer
import top.fifthlight.armorstand.ui.screen.AnimationScreen
import top.fifthlight.armorstand.ui.screen.ConfigScreen
import top.fifthlight.armorstand.ui.screen.ModelSwitchScreen
import top.fifthlight.armorstand.util.RendererManager
import top.fifthlight.armorstand.util.ThreadExecutorDispatcher
import top.fifthlight.blazerod.event.RenderEvents
import top.fifthlight.blazerod.model.ModelFileLoaders
import javax.swing.SwingUtilities

object ArmorStandClient : ArmorStand(), ClientModInitializer {
    private val LOGGER = LogUtils.getLogger()
    private val configKeyBinding = KeyBinding(
        "armorstand.keybinding.config",
        GLFW.GLFW_KEY_I,
        "armorstand.name"
    )
    private val animationKeyBinding = KeyBinding(
        "armorstand.keybinding.animation",
        GLFW.GLFW_KEY_K,
        "armorstand.name"
    )
    val modelSwitchKeyBinding = KeyBinding(
        "armorstand.keybinding.model_switch",
        GLFW.GLFW_KEY_U,
        "armorstand.name"
    )
    var debug: Boolean = false
        private set
    var debugBone: Boolean = false
        private set

    override lateinit var mainDispatcher: CoroutineDispatcher
        private set
    override lateinit var scope: CoroutineScope
        private set

    override fun onInitializeClient() {
        super.onInitialize()
        if (System.getProperty("armorstand.debug") == "true") {
            debug = true
            if (System.getProperty("armorstand.debug.bone") == "true") {
                debugBone = true
            }
            if (System.getProperty("armorstand.debug.gui") == "true") {
                System.setProperty("java.awt.headless", "false")
                SwingUtilities.invokeLater {
                    try {
                        ModelManagerDebugFrame().isVisible = true
                    } catch (ex: Exception) {
                        LOGGER.info("Failed to show debug windows", ex)
                    }
                }
            }
        }

        ModelFileLoaders.initialize()

        ConfigHolder.read()

        WorldRenderEvents.BEFORE_ENTITIES.register { context ->
            PlayerRenderer.startRenderWorld()
        }
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            PlayerRenderer.executeDraw()
        }
        WorldRenderEvents.END.register { context ->
            PlayerRenderer.endFrame()
        }
        RenderEvents.FLIP_FRAME.register {
            RendererManager.rotate()
        }

        ScreenEvents.UNLOCK_CURSOR.register { screen ->
            when (screen) {
                is ModelSwitchScreen -> false
                else -> true
            }
        }
        ScreenEvents.MOVE_VIEW.register { screen ->
            when (screen) {
                is ModelSwitchScreen -> false
                else -> true
            }
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { client ->
            mainDispatcher = ThreadExecutorDispatcher(client)
            scope = CoroutineScope(SupervisorJob() + mainDispatcher)
            runBlocking {
                ModelManagerHolder.initialize()
                NetworkModelSyncer.initialize()
                ClientModelPathManager.initialize()
                ModelInstanceManager.initialize()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { client ->
            runBlocking {
                ModelManagerHolder.close()
            }
            scope.cancel()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            ModelHashManager.clearHash()
        }
        ClientPlayNetworking.registerGlobalReceiver(PlayerModelUpdateS2CPayload.ID) { payload, context ->
            scope.launch {
                ModelHashManager.putModelHash(payload.uuid, payload.modelHash)
            }
        }
        KeyBindingHelper.registerKeyBinding(configKeyBinding)
        KeyBindingHelper.registerKeyBinding(animationKeyBinding)
        KeyBindingHelper.registerKeyBinding(modelSwitchKeyBinding)
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (client.player == null) {
                return@register
            }
            if (client.currentScreen != null) {
                return@register
            }
            if (configKeyBinding.isPressed) {
                client.setScreen(ConfigScreen(null))
            }
            if (animationKeyBinding.isPressed) {
                client.setScreen(AnimationScreen(null))
            }
            if (modelSwitchKeyBinding.isPressed) {
                client.setScreen(ModelSwitchScreen(null))
            }
        }
    }
}
