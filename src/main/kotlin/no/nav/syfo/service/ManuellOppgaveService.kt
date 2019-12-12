package no.nav.syfo.service

import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.oppdaterValidationResults

class ManuellOppgaveService(private val database: DatabaseInterface) {

    fun oppdaterValidationResuts(manueloppgaveId: String, validationResult: ValidationResult): Int =
        database.oppdaterValidationResults(manueloppgaveId, validationResult)

    fun hentManuellOppgaver(pasientFnr: String): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaver(pasientFnr)

    fun hentKomplettManuellOppgave(manueloppgaveId: String): ManuellOppgaveKomplett? =
        database.hentKomplettManuellOppgave(manueloppgaveId).firstOrNull()
}
