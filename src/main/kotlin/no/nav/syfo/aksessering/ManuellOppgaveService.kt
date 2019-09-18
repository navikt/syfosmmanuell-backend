package no.nav.syfo.aksessering

import no.nav.syfo.aksessering.db.hentManuellOppgave
import no.nav.syfo.db.DatabaseInterface

class ManuellOppgaveService(private val database: DatabaseInterface) {
    fun hentManuellOppgave(manueloppgaveId: String): ManuellOppgaveDTO =
        database.hentManuellOppgave(manueloppgaveId)
}
