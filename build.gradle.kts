import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "1.9.21"
    id("dev.extframework.common") version "1.0.6"
}

group = "dev.extframework.components"
version = "1.0-SNAPSHOT"

dependencies {
    boot()
    archives(configurationName = "api", mixin = true)
    commonUtil(configurationName = "api")
    archiveMapper()

    jobs(logging = true, progressSimple = true, configurationName = "api")
    artifactResolver()

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")

    implementation("dev.extframework:object-container:1.0-SNAPSHOT")
    testImplementation("dev.extframework:boot-test:$BOOT_VERSION")
}

abstract class ListAllDependencies : DefaultTask() {
    init {
        // Define the output file within the build directory
        val outputFile = project.buildDir.resolve("resources/test/dependencies.txt")
        outputs.file(outputFile)
    }

    @TaskAction
    fun listDependencies() {
        val outputFile = project.buildDir.resolve("resources/test/dependencies.txt")
        // Ensure the directory for the output file exists
        outputFile.parentFile.mkdirs()
        // Clear or create the output file
        outputFile.writeText("")

        val set = HashSet<String>()

        // Process each configuration that can be resolved
        project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            println("Processing configuration: ${configuration.name}")
            try {
                configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                    collectDependencies(dependency, set)
                }
            } catch (e: Exception) {
                println("Skipping configuration '${configuration.name}' due to resolution errors.")
            }
        }

        set.add("${this.project.group}:minecraft-bootstrapper:${this.project.version}\n")

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}

// Register the custom task in the project
val listAllDependencies = tasks.register<ListAllDependencies>("listAllDependencies")

// Ensure the copyTaskOutput runs before the test task
tasks.test {
    dependsOn(listAllDependencies)
}

common {
    publishing {
        publication {
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model.json").classifier =
                "component-model"

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