import groovy.namespace.QName
import groovy.util.Node
import groovy.util.NodeList

group = "net.yakclient.minecraft"
version = "1.0-SNAPSHOT"
repositories {
    mavenCentral()
    maven {
        name = "Durgan McBroom GitHub Packages"
        url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
        credentials {
            username = project.findProperty("dm.gpr.user") as? String ?: throw IllegalArgumentException("Need a Github package registry username!")
            password = project.findProperty("dm.gpr.key") as? String ?: throw IllegalArgumentException("Need a Github package registry key!")
        }
    }
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
}
dependencies {
    implementation("net.yakclient:launchermeta-handler:1.0-SNAPSHOT")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient:archives:1.1-SNAPSHOT")
    implementation(project(":"))
    implementation("net.yakclient:archive-mapper:1.1-SNAPSHOT") {
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
        create<MavenPublication>("def-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "minecraft-provider-def"


            pom {
                name.set("Minecraft Bootstrapper default")
                description.set("A minecraft boot application for default")
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

                    val toKeep = setOf(
                        "launchermeta-handler",
                        "jackson-dataformat-xml",
                        "jackson-module-kotlin",
                        "archives",
                        "archive-mapper",
                        "common-util",
                    )

                    val nodeList = asNode()["dependencies"] as NodeList
                    (nodeList.getAt("dependency"))
                        .map { it as Node }
                        .forEach {
                            (it.value() as NodeList)
                                .map { n -> n as Node }
                                .filter { n ->
                                    ((n.name() as QName).localPart).contains("artifactId")
                                }
                                .forEach { n ->
                                    if (!toKeep.contains(n.value() as String)) {
                                        val parent = n.parent()
                                        println(parent.parent().remove(parent))
                                    }
                                }
                        }
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