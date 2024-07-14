package dev.extframework.minecraft.bootstrapper.test

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.common.util.readInputStream
import dev.extframework.minecraft.bootstrapper.MinecraftProviderFinder
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.Test


class TestMinecraftProviderLoading {
    fun loadMinecraft(version: String) {
        val cache = Path.of("test-run").toAbsolutePath()

        val archiveGraph = ArchiveGraph(cache)
        val maven = MavenResolverProvider()

        launch(BootLoggerFactory()) {
            dev.extframework.minecraft.bootstrapper.loadMinecraft(
                version,
                SimpleMavenRepositorySettings.local(),
                cache,
                archiveGraph,
                maven.resolver,
                false
            )().merge()
        }
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
    @Test
    fun `Test 1_21 load`() {
        loadMinecraft("1.21")
    }


    @Test
    fun `Find correct mc version`() {
        val find = MinecraftProviderFinder(Files.createTempDirectory("test")).find("1.21")
        println(find)
        check(find.name == "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT")
    }
}