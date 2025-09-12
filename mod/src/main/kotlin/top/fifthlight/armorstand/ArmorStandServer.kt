package top.fifthlight.armorstand

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import top.fifthlight.armorstand.util.ThreadExecutorDispatcher

object ArmorStandServer: ArmorStand(), DedicatedServerModInitializer {
    override lateinit var mainDispatcher: CoroutineDispatcher
        private set
    override lateinit var scope: CoroutineScope
        private set

    override fun onInitializeServer() {
        super.onInitialize()
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            mainDispatcher = ThreadExecutorDispatcher(server)
            scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        }
    }
}