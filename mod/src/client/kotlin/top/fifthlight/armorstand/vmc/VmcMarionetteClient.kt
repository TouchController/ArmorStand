package top.fifthlight.armorstand.vmc

import com.illposed.osc.OSCBundle
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCPacket
import com.illposed.osc.transport.Transport
import com.illposed.osc.transport.udp.UDPTransport
import kotlinx.coroutines.*
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory
import top.fifthlight.armorstand.util.ThreadExecutorDispatcher
import top.fifthlight.blazerod.model.Expression
import top.fifthlight.blazerod.model.HumanoidTag

class VmcMarionetteClient(
    private val transport: Transport,
) : AutoCloseable {
    companion object {
        private val logger = LoggerFactory.getLogger(VmcMarionetteClient::class.java)
    }

    private val scope = CoroutineScope(ThreadExecutorDispatcher(MinecraftClient.getInstance()) + SupervisorJob())

    @Volatile
    private var closed = false

    private val stateHolder: VmcMarionetteStateHolder = VmcMarionetteStateHolder()

    fun getState() = stateHolder.read()

    private fun handlePacket(message: OSCPacket) {
        when (message) {
            is OSCBundle -> {
                message.packets.forEach(::handlePacket)
            }

            is OSCMessage -> handleMessage(message)
        }
    }

    private val pendingBlendValues = mutableMapOf<Expression.Tag, Float>()
    private fun handleMessage(message: OSCMessage) {
        when (message.address) {
            "/VMC/Ext/Root/Pos" -> {
                val name = (message.arguments.getOrNull(0) as? String) ?: return
                if (name != "root") {
                    return
                }
                val posX = (message.arguments.getOrNull(1) as? Float) ?: return
                val posY = (message.arguments.getOrNull(2) as? Float) ?: return
                val posZ = (message.arguments.getOrNull(3) as? Float) ?: return
                val rotX = (message.arguments.getOrNull(4) as? Float) ?: return
                val rotY = (message.arguments.getOrNull(5) as? Float) ?: return
                val rotZ = (message.arguments.getOrNull(6) as? Float) ?: return
                val rotW = (message.arguments.getOrNull(7) as? Float) ?: return
                stateHolder.write {
                    setRootTransform(posX, posY, posZ, rotX, rotY, rotZ, rotW)
                }
            }

            "/VMC/Ext/Bone/Pos" -> {
                val name = (message.arguments.getOrNull(0) as? String) ?: return
                val tag = HumanoidTag.fromVrmName(name.replaceFirstChar { it.lowercase() }) ?: return
                val posX = (message.arguments.getOrNull(1) as? Float) ?: return
                val posY = (message.arguments.getOrNull(2) as? Float) ?: return
                val posZ = (message.arguments.getOrNull(3) as? Float) ?: return
                val rotX = (message.arguments.getOrNull(4) as? Float) ?: return
                val rotY = (message.arguments.getOrNull(5) as? Float) ?: return
                val rotZ = (message.arguments.getOrNull(6) as? Float) ?: return
                val rotW = (message.arguments.getOrNull(7) as? Float) ?: return
                stateHolder.write {
                    setBoneTransform(tag, posX, posY, posZ, rotX, rotY, rotZ, rotW)
                }
            }

            "/VMC/Ext/Blend/Val" -> {
                val tag = (message.arguments.getOrNull(0) as? String) ?: return
                val expressionTag = Expression.Tag.fromVrm0Name(tag) ?: Expression.Tag.fromVrm1Name(tag) ?: return
                val value = (message.arguments.getOrNull(1) as? Float) ?: return
                pendingBlendValues[expressionTag] = value
            }

            "/VMC/Ext/Blend/Apply" -> {
                stateHolder.write {
                    pendingBlendValues.forEach {
                        setBlendShape(it.key, it.value)
                    }
                }
                pendingBlendValues.clear()
            }
        }
    }

    init {
        if (transport !is UDPTransport) {
            transport.connect()
        }
        require(transport.isBlocking) { "Require blocking transport" }
        scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val message = try {
                        transport.receive()
                    } catch (ex: Exception) {
                        if (!scope.isActive) {
                            // throw CancellationException
                            ensureActive()
                        }
                        logger.warn("Failed to receive OSC message", ex)
                        continue
                    }
                    handlePacket(message)
                    ensureActive()
                }
            } finally {
                if (transport !is UDPTransport) {
                    transport.disconnect()
                }
                transport.close()
            }
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        scope.cancel()
    }
}
