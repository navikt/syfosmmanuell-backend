package no.nav.syfo.vault

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.VaultCredentialService

class RenewVaultService(
    val vaultCredentialService: VaultCredentialService,
    val applicationState: ApplicationState
) {

    fun startRenewTasks() {
        GlobalScope.launch {
            try {
                Vault.renewVaultTokenTask(applicationState)
            } finally {
                applicationState.ready = false
            }
        }

        GlobalScope.launch {
            try {
                vaultCredentialService.runRenewCredentialsTask(applicationState)
            } finally {
                applicationState.ready = false
            }
        }
    }
}
