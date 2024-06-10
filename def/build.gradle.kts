import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

group = "dev.extframework.minecraft"

dependencies {
    boot()
    archives()
    commonUtil()
    archiveMapper(proguard = true)
    launcherMetaHandler()

    artifactResolver()
    jobs(logging = true, progressSimple = true)

    implementation(project(":"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
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