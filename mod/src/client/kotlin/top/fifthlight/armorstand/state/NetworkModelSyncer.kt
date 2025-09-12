package top.fifthlight.armorstand.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.PacketSender
import top.fifthlight.armorstand.ArmorStand
import top.fifthlight.armorstand.config.ConfigHolder
import top.fifthlight.armorstand.manage.ModelManagerHolder
import top.fifthlight.armorstand.network.ModelUpdateC2SPayload

object NetworkModelSyncer {
    private val packetSender = MutableStateFlow<PacketSender?>(null)

    fun initialize() {
        ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->
            packetSender.value = sender
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            packetSender.value = null
        }
        ArmorStand.instance.scope.launch {
            packetSender.collectLatest { sender ->
                var hasSetModel = false
                ConfigHolder.config
                    .map { Pair(it.modelPath, it.sendModelData) }
                    .distinctUntilChanged()
                    .collect { (modelPath, sendModelData) ->
                        if (sendModelData) {
                            val hash = modelPath?.let { ModelManagerHolder.instance.getModelByPath(it)?.hash }
                            sender?.sendPacket(ModelUpdateC2SPayload(hash))
                            hasSetModel = true
                        } else if (hasSetModel) {
                            sender?.sendPacket(ModelUpdateC2SPayload(null))
                            hasSetModel = false
                        }
                    }
            }
        }
    }
}
