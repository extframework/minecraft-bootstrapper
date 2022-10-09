package net.yakclient.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.createMaven
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.minecraft.bootstrapper.MinecraftProviderHandler
import java.nio.file.Path
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19 load`() {
        val dependencyGraph = createMaven(System.getProperty("user.dir")) { }
        val handler = MinecraftProviderHandler(
            dependencyGraph::writeResource,
            CachingDataStore(MavenDataAccess(Path.of(System.getProperty("user.dir")))),
            dependencyGraph
        ) {
            val descriptor =
                SimpleMavenDescriptor("net.yakclient.minecraft", "minecraft-provider-$it", "1.0-SNAPSHOT", null)

            SimpleMavenArtifactRequest(
                descriptor,
                includeScopes = setOf("compile")
            )
        }

        val provider = handler.get("1.19", SimpleMavenRepositorySettings.local())
        println(provider)
    }
}