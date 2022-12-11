package net.yakclient.minecraft.bootstrapper.test

import net.yakclient.boot.BootContext
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.createMavenProvider
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.withBootDependencies
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import java.io.File
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19_2 load`() {
        val cacheLocation = "${System.getProperty("user.dir")}${File.separator}cache"

        val bootstrapper = MinecraftBootstrapper()

        val bootContext = BootContext(
            DependencyProviders()
        )
        bootContext.dependencyProviders.add(
            createMavenProvider(cacheLocation, withBootDependencies())
        )

        bootstrapper.onEnable(
            ComponentContext(
                mapOf(
                    "version" to "1.19.2",
                    "repository" to "/Users/durgan/.m2/repository",
                    "repositoryType" to "LOCAL",
                    "cache" to cacheLocation,
                    "providerVersionMappings" to "file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json",
                    "mcArgs" to "--version;1.19.2;--accessToken;"
                ),
                bootContext
            ),
        )

        bootstrapper.minecraftHandler.loadMinecraft()
        bootstrapper.minecraftHandler.startMinecraft()
    }
}