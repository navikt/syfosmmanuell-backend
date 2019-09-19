package no.nav.syfo.service

import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.oppdaterValidationResults

class ManuellOppgaveService(private val database: DatabaseInterface) {

    fun oppdaterValidationResuts(manueloppgaveId: String, validationResult: ValidationResult): Int =
        database.oppdaterValidationResults(manueloppgaveId, validationResult)

    fun hentManuellOppgave(manueloppgaveId: String): ManuellOppgaveDTO? =
        database.hentManuellOppgave(manueloppgaveId).firstOrNull()

    fun hentKomplettManuellOppgave(manueloppgaveId: String): ManuellOppgave? =
        database.hentKomplettManuellOppgave(manueloppgaveId).firstOrNull()
}
