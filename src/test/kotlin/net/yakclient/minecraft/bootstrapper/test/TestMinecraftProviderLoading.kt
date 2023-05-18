package net.yakclient.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentContext
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import java.io.File
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19_2 load`() {
        val cacheLocation = "${System.getProperty("user.dir")}${File.separator}cache"

        val bootstrapper = MinecraftBootstrapper()

        val boot = BootInstance.new(cacheLocation)

        bootstrapper.onEnable(
            ComponentContext(
                mapOf(
                    "version" to "1.19.2",
                    "repository" to mavenLocal,
                    "repositoryType" to "LOCAL",
                    "cache" to cacheLocation,
                    "providerVersionMappings" to "http://maven.yakclient.net/public/mc-version-mappings.json",
                    "mcArgs" to "--version;1.19.2;--accessToken;"
                ),
                boot
            ),
        )

        bootstrapper.minecraftHandler.loadMinecraft()
        bootstrapper.minecraftHandler.startMinecraft()
    }
}