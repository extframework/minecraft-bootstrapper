package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.common.util.immutableLateInit
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.logging.Level
import java.util.logging.Logger

public enum class MinecraftRepositoryType(
    public val settingsProvider: (String) -> SimpleMavenRepositorySettings,
) {
    DEFAULT({
        SimpleMavenRepositorySettings.default(it, preferredHash = HashType.SHA1)
    }),
    LOCAL({
        SimpleMavenRepositorySettings.local(path = it, preferredHash = HashType.SHA1)
    })
}

public class MinecraftBootstrapper(
    private val boot: BootInstance,
    private val configuration: MinecraftBootstrapperConfiguration
) : ComponentInstance<MinecraftBootstrapperConfiguration> {
    private val logger = Logger.getLogger(this::class.simpleName)
    public var minecraftHandler: MinecraftHandler<*> by immutableLateInit()
        private set

    override fun start() {
        logger.log(Level.INFO, "Minecraft Bootstrapper is enabling.")

        val cachePath = boot.location resolve configuration.cache

        val providerVersionsPath = cachePath resolve "minecraft-versions.json"

        val versionMappings = providerVersionsPath.toFile().takeIf(File::exists)
            ?.let { ObjectMapper().readValue<Map<String, String>>(it) } ?: HashMap()

//        val handler = MinecraftProviderHandler(
//            { request, resource ->
//                val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
//                val jarPath = cachePath resolve descriptor.group.replace(
//                    '.',
//                    File.separatorChar
//                ) resolve descriptor.artifact resolve descriptor.version resolve jarName
//
//                if (!Files.exists(jarPath)) {
//                    Channels.newChannel(resource.open()).use { cin ->
//                        jarPath.make()
//                        FileOutputStream(jarPath.toFile()).use { fout ->
//                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                        }
//                    }
//                }
//
//                jarPath
//            },
//            CachingDataStore(MavenDataAccess(cachePath)),
//            boot.dependencyProviders["simple-maven"]?.graph as DependencyGraph<SimpleMavenArtifactRequest, *, SimpleMavenRepositorySettings>,
//        ) {
//            SimpleMavenArtifactRequest(
//                versionMappings[it] ?: run {
//                    providerVersionsPath.make()
//
//                    Channels.newChannel(URL(configuration.versionMappings).openStream()).use { cin ->
//                        FileOutputStream(providerVersionsPath.toFile()).use { fout: FileOutputStream ->
//                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                        }
//                    }
//
//                    ObjectMapper().readValue<Map<String, String>>(providerVersionsPath.toFile())
//                }[it] ?: throw IllegalArgumentException("Failed to find version provider for version '$it'"),
//                includeScopes = setOf("compile", "runtime", "import"),
//            )
//        }

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

        val graph = MinecraftHandlerDependencyGraph(cachePath, configuration.repository)

        val descriptor = getProviderFor(configuration.mcVersion)

        minecraftHandler = MinecraftHandler(
            configuration.mcVersion,
            cachePath,
            graph.load(descriptor),
            configuration.mcArgs.toTypedArray(),
            configuration.applyBasicArgs
        )
    }

    override fun end() {
        minecraftHandler.shutdownMinecraft()
    }


}