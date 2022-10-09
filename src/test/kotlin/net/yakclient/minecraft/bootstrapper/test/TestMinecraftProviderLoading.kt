package net.yakclient.minecraft.bootstrapper.test

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.createMaven
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.minecraft.bootstrapper.MinecraftProviderHandler
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import kotlin.test.Test

class TestMinecraftProviderLoading {
    @Test
    fun `Test 1_19 load`() {
        val cacheLocation = System.getProperty("user.dir")
        val dependencyGraph = createMaven(cacheLocation) { }
        val handler = MinecraftProviderHandler(
            { request, resource ->
                val descriptor by request::descriptor

                val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
                val jarPath = Path.of(cacheLocation) resolve descriptor.group.replace(
                    '.',
                    File.separatorChar
                ) resolve descriptor.artifact resolve descriptor.version resolve jarName

                if (!Files.exists(jarPath)) {
                    Channels.newChannel(resource.open()).use { cin ->
                        jarPath.make()
                        FileOutputStream(jarPath.toFile()).use { fout ->
                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                        }
                    }
                }

                jarPath
            },
            CachingDataStore(MavenDataAccess(Path.of(cacheLocation))),
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