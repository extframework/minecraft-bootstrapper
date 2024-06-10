package dev.extframework.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.test.testBootInstance
import dev.extframework.common.util.readInputStream
import dev.extframework.minecraft.bootstrapper.ExtraClassProvider
import dev.extframework.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
import dev.extframework.minecraft.bootstrapper.MinecraftBootstrapperFactory
import runBootBlocking
import java.lang.IllegalStateException
import java.nio.file.Path
import java.util.*
import kotlin.test.Test


class TestMinecraftProviderLoading {
    fun loadMinecraft(version: String) {
        val dependencies = this::class.java.getResource("/dependencies.txt")?.openStream()?.use {
            val fileStr = String(it.readInputStream())
            fileStr.split("\n").toSet()
        }?.filterNot { it.isBlank() } ?: throw IllegalStateException("Cant load dependencies?")
        val bootInstance = testBootInstance(
            mapOf(), location = Path.of("test-run").toAbsolutePath(),
            (dependencies).mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
        )
        println(bootInstance.location.toAbsolutePath())

        val instance = MinecraftBootstrapperFactory(bootInstance).new(
            MinecraftBootstrapperConfiguration(
                version,
                SimpleMavenRepositorySettings.local(preferredHash = ResourceAlgorithm.SHA1),
                "mc",
                this::class.java.getResource("/mc-version-test-mappings.json")!!.toString(),
            )
        )
        runBootBlocking {
            instance.start()().merge()
            instance.minecraftHandler.loadMinecraft(ClassLoader.getSystemClassLoader(), object : ExtraClassProvider {
                override fun getByteArray(name: String): ByteArray? {
                    return null
                }
            })().merge()
        }

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