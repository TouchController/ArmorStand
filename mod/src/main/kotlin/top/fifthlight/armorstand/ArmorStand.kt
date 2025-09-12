package top.fifthlight.armorstand

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import top.fifthlight.armorstand.network.ModelUpdateC2SPayload
import top.fifthlight.armorstand.network.PlayerModelUpdateS2CPayload
import top.fifthlight.armorstand.server.ServerModelPathManager


abstract class ArmorStand : ModInitializer {
    abstract val scope: CoroutineScope
    abstract val mainDispatcher: CoroutineDispatcher

    companion object {
        lateinit var instance: ArmorStand
    }

    private var server: MinecraftServer? = null

    override fun onInitialize() {
        instance = this
        PayloadTypeRegistry.playS2C().register(PlayerModelUpdateS2CPayload.ID, PlayerModelUpdateS2CPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ModelUpdateC2SPayload.ID, ModelUpdateC2SPayload.CODEC)

        ServerModelPathManager.onUpdateListener = { uuid, hash ->
            server?.let { server ->
                val payload = PlayerModelUpdateS2CPayload(uuid, hash)
                for (player in PlayerLookup.all(server)) {
                    if (player.uuid == uuid) {
                        continue
                    }
                    ServerPlayNetworking.send(player, payload)
                }
            }
        }
        ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            for ((uuid, hash) in ServerModelPathManager.getModels()) {
                if (handler.player.uuid == uuid) {
                    continue
                }
                sender.sendPacket(PlayerModelUpdateS2CPayload(uuid, hash))
            }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            ServerModelPathManager.update(handler.player.uuid, null)
        }
        ServerPlayNetworking.registerGlobalReceiver(ModelUpdateC2SPayload.ID) { payload, context ->
            ServerModelPathManager.update(context.player().uuid, payload.modelHash)
        }
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            this.server = server
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            ServerModelPathManager.clear()
        }
    }
}
