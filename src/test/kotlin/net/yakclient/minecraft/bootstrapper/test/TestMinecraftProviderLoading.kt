package net.yakclient.minecraft.bootstrapper.test

import net.yakclient.boot.component.ComponentLoadContext
import net.yakclient.boot.initMaven
import net.yakclient.boot.withBootDependencies
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import java.io.File
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19_2 load`() {
        val cacheLocation = "${System.getProperty("user.dir")}${File.separator}cache"

        val bootstrapper = MinecraftBootstrapper()

        bootstrapper.onEnable(
            ComponentLoadContext(
                mapOf(
                    "version" to "1.19.2",
                    "repository" to "/Users/durgan/.m2/repository",
                    "repositoryType" to "LOCAL",
                    "cache" to cacheLocation,
                    "providerVersionMappings" to "file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json",
                    "mcArgs" to "--version;1.19.2;--accessToken;"
                ),
                initMaven(cacheLocation, withBootDependencies { })
            ),
        )

        bootstrapper.minecraftHandler.loadMinecraft()
        bootstrapper.minecraftHandler.startMinecraft()
    }
}