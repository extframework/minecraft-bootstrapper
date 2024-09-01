import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "2.0.0"
    id("dev.extframework.common") version "1.0.14"
}

group = "dev.extframework"

dependencies {
    boot()
    archives(configurationName = "api", mixin = true)
    commonUtil(configurationName = "api")
    objectContainer()
    archiveMapper(proguard = true)

    jobs(logging = true, progressSimple = true, configurationName = "api")
    artifactResolver()

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
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


allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    version = "2.0.4-SNAPSHOT"

    repositories {
        mavenLocal()
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