package no.nav.syfo.diagnose

enum class Diagnosekode(val kithCode: String, val infotrygdCode: String) {
    ICPC_2("2.16.578.1.12.4.1.1.7170", "5"),
    ICD_10("2.16.578.1.12.4.1.1.7110", "3")
}

operator fun Array<Diagnosekode>.contains(code: String): Boolean = code in map { it.kithCode }
