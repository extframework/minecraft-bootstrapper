import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "1.9.21"
    id("dev.extframework.common") version "1.0.6"
}

group = "dev.extframework.components"
version = "2.0-SNAPSHOT"

dependencies {
    boot(version = "3.0-SNAPSHOT")
    archives(configurationName = "api", mixin = true)
    commonUtil(configurationName = "api")
    archiveMapper()

    jobs(logging = true, progressSimple = true, configurationName = "api")
    artifactResolver()

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")

    implementation("dev.extframework:object-container:1.0-SNAPSHOT")
}


common {
    publishing {
        publication {
            artifactId = "minecraft-bootstrapper"

            pom {
                name.set("Minecraft Bootstrapper")
                description.set("A Boot Application for minecraft")
                url.set("https://github.com/extframewor/minecraft-bootstrapper")
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")


    repositories {
        mavenCentral()
        extFramework()
        mavenLocal()
    }

    common {
        defaultJavaSettings()
        publishing {
            repositories {
                extFramework(credentials = propertyCredentialProvider)
            }
            publication {
                withJava()
                withSources()
                withDokka()

                commonPom {
                    defaultDevelopers()
                    gnuLicense()
                    withExtFrameworkRepo()
                    extFrameworkScm("minecraft-bootstrapper")
                }
            }
        }
    }

    kotlin {
        explicitApi()
    }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }
}