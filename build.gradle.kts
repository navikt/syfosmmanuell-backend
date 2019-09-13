import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0-SNASHOT"


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
