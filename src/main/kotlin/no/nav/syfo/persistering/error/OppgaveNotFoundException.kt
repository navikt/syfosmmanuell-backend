package no.nav.syfo.persistering.error

import java.lang.RuntimeException

class OppgaveNotFoundException(override val message: String) : RuntimeException(message)
