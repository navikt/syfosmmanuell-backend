import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0-SNASHOT"

val ktorVersion = "1.2.3"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.1"
val prometheusVersion = "0.5.0"

plugins {
    kotlin("jvm") version "1.3.50"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

repositories {
    mavenCentral()
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url = "https://oss.sonatype.org/content/groups/staging/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")

    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
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

}
