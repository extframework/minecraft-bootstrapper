package net.yakclient.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.test.testBootInstance
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.Test

fun main() {
    TestMinecraftProviderLoading().loadMinecraft("1.20.1")
}

class TestMinecraftProviderLoading {
    fun loadMinecraft(version: String) {
        val bootInstance = testBootInstance(mapOf(), location = Files.createTempDirectory("Load Minecraft"))
        println(bootInstance.location)

        val instance = MinecraftBootstrapperFactory(bootInstance).new(MinecraftBootstrapperConfiguration(
            version,
            SimpleMavenRepositorySettings.local(preferredHash = HashType.SHA1),
            "mc",
            this::class.java.getResource("/mc-version-test-mappings.json")!!.toString(),
            listOf(
                "--accessToken", ""
            )))
        instance.start()
        instance.minecraftHandler.loadMinecraft(ClassLoader.getSystemClassLoader())

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