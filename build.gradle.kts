plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "net.yakclient.components"

dependencies {
    api("net.yakclient:archives:1.1-SNAPSHOT")
    implementation("net.yakclient:archives-mixin:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("net.yakclient:boot:2.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT") {
        isChanging = true
    }
    api("net.yakclient:common-util:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient:archive-mapper:1.2.1-SNAPSHOT")
    testImplementation("net.yakclient:boot-test:2.1-SNAPSHOT")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT")
    api("com.durganmcbroom:jobs:1.2-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-logging:1.2-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-progress:1.2-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-progress-simple:1.2-SNAPSHOT") {
        isChanging = true
    }
}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
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

//tasks.register<Copy>("copyTaskOutput") {
//    from(listAllDependencies.map { it.outputs.files.asFileTree }) // specify the directory where your task outputs files
//    into("/build/resources/test") // specify the test resources directory
//}

// Ensure the copyTaskOutput runs before the test task
tasks.test {
    dependsOn(listAllDependencies)
}

val printDependencies by tasks.creating {
    configurations.implementation.get().dependencies.forEach {
        println("${it.group}:${it.name}:${it.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("minecraft-bootstrapper-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model.json").classifier =
                "component-model"

            artifactId = "minecraft-bootstrapper"

            pom {
                name.set("Minecraft Bootstrapper")
                description.set("A Boot Application for minecraft")
                url.set("https://github.com/yakclient/minecraft-bootstrapper")

                packaging = "jar"

                developers {
                    developer {
                        id.set("Chestly")
                        name.set("Durgan McBroom")
                    }
                }
                withXml {
                    val repositoriesNode = asNode().appendNode("repositories")
                    val yakclientRepositoryNode = repositoriesNode.appendNode("repository")
                    yakclientRepositoryNode.appendNode("id", "yakclient")
                    yakclientRepositoryNode.appendNode("url", "http://maven.yakclient.net/snapshots")
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/minecraft-bootstrapper")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/minecraft-bootstrapper.git")
                    url.set("https://github.com/yakclient/minecraft-bootstrapper")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
    }

    publishing {
        repositories {
            if (project.hasProperty("maven-user") && project.hasProperty("maven-secret")) maven {
                logger.quiet("Maven user and password found.")
                val repo = if ((version as String).endsWith("-SNAPSHOT")) "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")
                logger.info("Publishing to repository: '$url'")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-secret") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            } else logger.quiet("Maven user and password not found.")
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

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.compileJava {
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }
}