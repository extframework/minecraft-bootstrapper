package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.ResourceAlgorithm.SHA1
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.ComponentInstance
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.immutableLateInit
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels

public enum class MinecraftRepositoryType(
    public val settingsProvider: (String) -> SimpleMavenRepositorySettings,
) {
    DEFAULT({
        SimpleMavenRepositorySettings.default(it, preferredHash = SHA1)
    }),
    LOCAL({
        SimpleMavenRepositorySettings.local(path = it, preferredHash = SHA1)
    })
}

public class MinecraftBootstrapper(
    private val boot: BootInstance,
    private val configuration: MinecraftBootstrapperConfiguration
) : ComponentInstance<MinecraftBootstrapperConfiguration> {
    public var minecraftHandler: MinecraftHandler<*> by immutableLateInit()
        private set

    override fun start() : Job<Unit> = job {
        val cachePath = boot.location resolve configuration.cache

        val providerVersionsPath = cachePath resolve "minecraft-versions.json"

        val versionMappings = providerVersionsPath.toFile().takeIf(File::exists)
            ?.let { ObjectMapper().readValue<Map<String, String>>(it) } ?: HashMap()

        fun getProviderFor(mcVersion: String): SimpleMavenDescriptor {
            val descString = versionMappings[mcVersion] ?: run {
                providerVersionsPath.make()

                Channels.newChannel(URL(configuration.versionMappings).openStream()).use { cin ->
                    FileOutputStream(providerVersionsPath.toFile()).use { fout: FileOutputStream ->
                        fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                    }
                }

                ObjectMapper().readValue<Map<String, String>>(providerVersionsPath.toFile())
            }[mcVersion] ?: throw IllegalArgumentException("Unknown minecraft version: '$mcVersion'")
            return SimpleMavenDescriptor.parseDescription(descString)
                ?: throw IllegalArgumentException("Failed to parse maven descriptor minecraft provider : '$descString'.")
        }

        val descriptor = getProviderFor(configuration.mcVersion)

        job(JobName("Load minecraft handler")) {
            minecraftHandler = MinecraftHandler(
                configuration.mcVersion,
                cachePath,
                boot.archiveGraph.loadProvider(descriptor, boot.dependencyTypes.get("simple-maven")!!.resolver as MavenLikeResolver<*, *>, configuration.repository)().merge(),
                boot.archiveGraph
            )

            minecraftHandler.loadReference()().merge()
        }().merge()
    }

    override fun end() : Job<Unit> {
        minecraftHandler.shutdownMinecraft()

        return SuccessfulJob {  }
    }
}