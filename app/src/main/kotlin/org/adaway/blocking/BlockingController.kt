package org.adaway.blocking

import kotlinx.coroutines.flow.first
import org.adaway.blocklist.StatusMessage
import org.adaway.blocklist.StatusMessages
import org.adaway.blocklist.UpdateOutcome
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.SettingsRepository
import org.adaway.root.RootController
import org.adaway.root.RootError
import org.adaway.vpn.StartResult
import org.adaway.vpn.VpnController

/** The blocking of the method in use. Turning the VPN on needs its consent first. */
class BlockingController(
    private val settingsRepository: SettingsRepository,
    private val vpnController: VpnController,
    private val rootController: RootController,
    private val statusMessages: StatusMessages,
) {
    /** Returns the failure of the root installation, if any. */
    suspend fun enable(): RootError? = when (method()) {
        BlockingMethod.VPN -> {
            if (vpnController.enable() == StartResult.STARTED) statusMessages.post(StatusMessage.Outcome(UpdateOutcome.APPLIED))
            null
        }
        BlockingMethod.ROOT -> rootController.install()
        BlockingMethod.UNSET -> null
    }

    suspend fun disable() {
        when (method()) {
            BlockingMethod.VPN -> vpnController.disable()
            BlockingMethod.ROOT -> rootController.revert()
            BlockingMethod.UNSET -> {}
        }
    }

    /** The blocking on takes the new final list. */
    suspend fun onBlockListApplied() {
        when (method()) {
            BlockingMethod.VPN -> vpnController.onBlockListApplied()
            BlockingMethod.ROOT -> rootController.reinstall()
            BlockingMethod.UNSET -> {}
        }
    }

    private suspend fun method() = settingsRepository.settings.first().blockingMethod
}
