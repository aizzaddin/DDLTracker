plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "1.9.25"
}

group = "com.wildanaizzaddin"
version = "1.1.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IU")
    plugins.set(listOf("com.intellij.database"))
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
    testImplementation(kotlin("test"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("")
    }

    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(17)
}
