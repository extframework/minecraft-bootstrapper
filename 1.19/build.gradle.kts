plugins {
}

group = "net.yakclient.minecraft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.arrow-kt:arrow-core:1.1.2")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient:archives:1.0-SNAPSHOT")
    implementation(project(":"))
    implementation("net.yakclient:archive-mapper:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:boot:1.0-SNAPSHOT") {
        exclude(group = "com.durganmcbroom", module = "artifact-resolver")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven")

        exclude(group = "com.durganmcbroom", module = "artifact-resolver-jvm")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven-jvm")
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:common-util:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}


task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("1.19-maven") {
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact(tasks["jar"])

            artifactId = "minecraft-provider-1.19"

            pom {
                name.set("Minecraft Bootstrapper 1.19")
                description.set("A minecraft boot application for 1.19")
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

                    val dependenciesNode = asNode().appendNode("dependencies")

                    val archiveMapperNode = dependenciesNode.appendNode("dependency")
                    archiveMapperNode.appendNode("groupId", "net.yakclient")
                    archiveMapperNode.appendNode("artifactId", "archive-mapper")
                    archiveMapperNode.appendNode("version", "1.0-SNAPSHOT")
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

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf("--add-reads", "kotlin.stdlib=kotlinx.coroutines.core.jvm")
}