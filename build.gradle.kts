group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.9.0"
val ktorVersion = "2.3.12"
val logbackVersion = "1.5.8"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val sykmeldingVersion = "2.0.1"
val fellesformatVersion = "2.0.1"
val kithHodemeldingVersion = "2.0.1"
val jacksonVersion = "2.18.0"
val mockkVersion = "1.13.12"
val postgresVersion = "42.7.4"
val flywayVersion = "10.18.2"
val hikariVersion = "6.0.0"
val javaTimeAdapterVersion = "1.1.3"
val kotestVersion = "5.9.1"
val nimbusdsVersion = "9.41.2"
val caffeineVersion = "3.1.8"
val testContainerVersion = "1.20.2"
val kotlinVersion = "2.0.20"
val kafkaVersion = "3.8.0"
val commonsCodecVersion = "1.17.1"
val logbacksyslog4jVersion = "1.0.0"
val ktfmtVersion = "0.44"
val snappyJavaVersion = "1.1.10.7"
val commonsCompressVersion = "1.27.1"

plugins {
    id("application")
    kotlin("jvm") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.3"
    id("com.diffplug.spotless") version "6.25.0"
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
    implementation("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")

    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    implementation("com.papertrailapp:logback-syslog4j:$logbacksyslog4jVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")

    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    constraints {
        testImplementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("overrides vulnerable dependency from org.testcontainers:postgresql")
        }
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
}


tasks {
    shadowJar {
mergeServiceFiles {
     setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
 }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }


    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
