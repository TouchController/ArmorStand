package top.fifthlight.armorstand.ui.state

import top.fifthlight.armorstand.vmc.VmcMarionetteManager

data class VmcConfigScreenState(
    val state: VmcMarionetteManager.State = VmcMarionetteManager.State.Stopped,
    val portNumber: Int = 9000,
)
