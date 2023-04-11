package no.nav.syfo.authorization.db

import no.nav.syfo.db.DatabaseInterface
import java.sql.ResultSet

fun DatabaseInterface.getFnr(oppgaveId: Int): String? {
    return connection.use { connection ->
        connection.prepareStatement(
            "SELECT pasientfnr FROM MANUELLOPPGAVE WHERE oppgaveid=?",
        ).use { preparedStatement ->
            preparedStatement.setInt(1, oppgaveId)
            preparedStatement.executeQuery().toFnr()
        }
    }
}

private fun ResultSet.toFnr(): String? {
    return when (next()) {
        true -> getString("pasientfnr")
        false -> null
    }
}
