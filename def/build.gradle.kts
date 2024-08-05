import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.dm.resourceApi

group = "dev.extframework.minecraft"

dependencies {
    boot()
    archives()
    commonUtil()
    archiveMapper(proguard = true)
    launcherMetaHandler()
    resourceApi()

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

//    minHeapSize = "512m"
//    maxHeapSize = "1024m"
}