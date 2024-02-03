package net.yakclient.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.test.testBootInstance
import net.yakclient.minecraft.bootstrapper.ExtraClassProvider
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashSet
import kotlin.test.Test

fun main() {
    TestMinecraftProviderLoading().loadMinecraft("1.20.1")
}

class TestMinecraftProviderLoading {
    fun loadMinecraft(version: String) {
        val bootInstance = testBootInstance(
            mapOf(), location = Path.of("test-run"),
            setOf(
                "net.yakclient:archives:1.1-SNAPSHOT",
                "net.yakclient:archives-mixin:1.1-SNAPSHOT",
                "io.arrow-kt:arrow-core:1.1.2",
                "org.jetbrains.kotlinx:kotlinx-cli:0.3.5",
                "net.yakclient:boot:2.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-jvm:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven-jvm:1.0-SNAPSHOT",
                "net.bytebuddy:byte-buddy-agent:1.12.18",
                "net.yakclient:common-util:1.0-SNAPSHOT",
                "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4",
                "net.yakclient:archive-mapper:1.2-SNAPSHOT",
                "net.yakclient:object-container:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-logging:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress-simple:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress-simple-jvm:1.0-SNAPSHOT",
                "junit:junit:4.13.1",
                "org.powermock:powermock-module-junit4:2.0.0",
                "javax.measure:jsr-275:0.9.1",
                "org.powermock:powermock-core:2.0.0",
                "org.powermock:powermock-api-mockito2:2.0.0",
                "jakarta.xml.bind:jakarta.xml.bind-api:3.0.1",
                "com.fasterxml.jackson.module:jackson-module-jakarta-xmlbind-annotations:2.13.4",
                "com.sun.xml.stream:sjsxp:1.0.2",
                "org.osgi:osgi.core:5.0.0",
                "junit:junit:4.13.2",
                "biz.aQute.bnd:biz.aQute.bnd.annotation:6.3.1",

            ).mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
        )
        println(bootInstance.location.toAbsolutePath())

        val instance = MinecraftBootstrapperFactory(bootInstance).new(
            MinecraftBootstrapperConfiguration(
                version,
                SimpleMavenRepositorySettings.local(preferredHash = HashType.SHA1),
                "mc",
                this::class.java.getResource("/mc-version-test-mappings.json")!!.toString(),
//            listOf(
//                "--accessToken", ""
            )
        )
        instance.start()
        instance.minecraftHandler.loadMinecraft(ClassLoader.getSystemClassLoader(), object : ExtraClassProvider {
            override fun getByteArray(name: String): ByteArray? {
                return null
            }
        })

        instance.minecraftHandler.startMinecraft(
            arrayOf(
                "--version", "1.20.1",
                "--assetsDir",
                instance.minecraftHandler.minecraftReference.runtimeInfo.assetsPath.toString() + "/",
                "--assetIndex",
                instance.minecraftHandler.minecraftReference.runtimeInfo.assetsName,
                "--gameDir",
                instance.minecraftHandler.minecraftReference.runtimeInfo.gameDir.toString(),
                "--accessToken", "",
            )
        )
        println("back here")
    }

    @Test
    fun `Test 1_19_2 load`() {
        loadMinecraft("1.19.2")
    }

    @Test
    fun `Test 1_20_1 load`() {
        loadMinecraft("1.20.1")
    }
}