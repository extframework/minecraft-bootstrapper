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
import java.util.*
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19_2 load`() {
        val bootInstance = testBootInstance(mapOf(), location = Files.createTempDirectory(UUID.randomUUID().toString()))

        val instance = MinecraftBootstrapperFactory(bootInstance).new(MinecraftBootstrapperConfiguration(
                "1.19.2",
                SimpleMavenRepositorySettings.local(preferredHash = HashType.SHA1),
                "mc",
                "http://maven.yakclient.net/public/mc-version-mappings.json",
                listOf(
                        "--version", "1.19.2", "--accessToken", ""
                )))
        instance.start()
        instance.minecraftHandler.loadMinecraft()

        instance.minecraftHandler.startMinecraft()
        println("back here") // This should be expected to print if all goes well :)
    }
}