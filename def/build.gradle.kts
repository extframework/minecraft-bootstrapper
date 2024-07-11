import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

group = "dev.extframework.minecraft"

version = "1.0-SNAPSHOT"

dependencies {
    boot(version = "3.0-SNAPSHOT")
    archives()
    commonUtil()
    archiveMapper(proguard = true)
    launcherMetaHandler()

    artifactResolver()
    jobs(logging = true, progressSimple = true)

    implementation(project(":"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

common {
    publishing {
        publication {
            artifactId = "minecraft-provider-def"

            pom {
                name.set("Minecraft Bootstrapper default")
                description.set("A minecraft boot application for default")
                url.set("https://github.com/extframewor/minecraft-bootstrapper")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf("--add-reads", "kotlin.stdlib=kotlinx.coroutines.core.jvm")
}