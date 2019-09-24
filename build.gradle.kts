import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.2.2"
val ktorVersion = "1.2.3"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.1"
val prometheusVersion = "0.5.0"
val smCommonVersion = "2019.09.03-11-07-64032e3b6381665e9f9c0914cef626331399e66d"
val jacksonVersion = "2.9.7"
val spekVersion = "2.0.6"
val kluentVersion = "1.39"
val kafkaEmbeddedVersion = "2.1.1"
val mockkVersion = "1.9.3"
val postgresVersion = "42.2.5"
val h2Version = "1.4.197"
val flywayVersion = "5.2.4"
val hikariVersion = "3.3.0"
val vaultJavaDriveVersion = "3.1.0"
val navArbeidsfordelingv1Version = "1.2019.07.11-06.47-b55f47790a9d"
val navPersonv3Version = "1.2019.07.11-06.47-b55f47790a9d"
val jaxwsApiVersion = "2.3.1"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxbApiVersion = "2.4.0-b180830.0359"
val javaxActivationVersion = "1.1.1"
val cxfVersion = "3.2.7"
val postgresEmbeddedVersion = "0.13.1"
val jaxbTimeAdaptersVersion = "1.1.3"

plugins {
    kotlin("jvm") version "1.3.50"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    id("org.jmailen.kotlinter") version "2.1.0"
}

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://oss.sonatype.org/content/groups/staging/")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "http://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("no.nav.syfo.sm:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-rest-sts:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-ws:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-networking:$smCommonVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")

    implementation("no.nav.tjenestespesifikasjoner:person-v3-tjenestespesifikasjon:$navPersonv3Version")
    implementation("no.nav.tjenestespesifikasjoner:arbeidsfordeling-v1-tjenestespesifikasjon:$navArbeidsfordelingv1Version")

    implementation ("com.migesok:jaxb-java-time-adapters:$jaxbTimeAdaptersVersion")
    implementation ("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation ("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")
    implementation ("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }
    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
    }
}
