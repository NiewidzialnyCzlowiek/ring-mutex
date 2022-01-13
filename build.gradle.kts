import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    application
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "io.bartlomiejszal"
version = "1.0-SNAPSHOT"

val fullMainClassName = "io.bartlomiejszal.App"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.zeromq:jeromq:0.5.2")
    implementation("com.sksamuel.hoplite:hoplite-core:1.4.16")
    implementation("com.sksamuel.hoplite:hoplite-yaml:1.4.16")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

project.setProperty("mainClassName", fullMainClassName)
tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("ring-mutex-peer")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to fullMainClassName))
        }
    }
}

application {
    mainClass.set(fullMainClassName)
}
