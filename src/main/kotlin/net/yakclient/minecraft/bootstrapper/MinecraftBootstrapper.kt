package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.component.SoftwareComponent
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.immutableLateInit
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

private enum class MinecraftRepositoryType(
    val settingsProvider: (String) -> SimpleMavenRepositorySettings,
) {
    DEFAULT({
        SimpleMavenRepositorySettings.default(it, preferredHash = HashType.SHA1)
    }),
    LOCAL({
        SimpleMavenRepositorySettings.local(path = it, preferredHash = HashType.SHA1)
    })
}

public class MinecraftBootstrapper : SoftwareComponent {
    private val logger = Logger.getLogger(this::class.simpleName)
    public var minecraftHandler: MinecraftHandler<*> by immutableLateInit()
        private set

    init {
        checkNotInitialized()
        instance = this
    }

    public companion object {
        private var initialized: Boolean = false
        public var instance: MinecraftBootstrapper by immutableLateInit()

        public fun checkNotInitialized() {
            check(!initialized) { "Minecraft already initialized" }

        }

        public fun checkInitialized() {
            check(initialized) { "Minecraft not initialized yet!" }
        }
    }

    override fun onEnable(context: ComponentContext) {
        logger.log(Level.INFO, "Minecraft Bootstrapper is enabling.")

        val version by context.configuration
        val repository by context.configuration
        val repositoryType by context.configuration
        val type = MinecraftRepositoryType.valueOf(repositoryType)
        val cache by context.configuration
        val providerVersionMappings by context.configuration
        val mcArgs by context.configuration

        val cachePath = Path.of(cache)

        val providerVersionsPath = cachePath resolve "minecraft-versions.json"

        val versionMappings = providerVersionsPath.toFile().takeIf(File::exists)
            ?.let { ObjectMapper().readValue<Map<String, String>>(it) } ?: HashMap()

        val handler = MinecraftProviderHandler(
            { request, resource ->
                val descriptor by request::descriptor

                val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
                val jarPath = cachePath resolve descriptor.group.replace(
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
            CachingDataStore(MavenDataAccess(cachePath)),
            context.boot.dependencyProviders["simple-maven"]?.graph as DependencyGraph<SimpleMavenArtifactRequest, *, SimpleMavenRepositorySettings>,
        ) {
            SimpleMavenArtifactRequest(
                versionMappings[it] ?: run {
                    providerVersionsPath.make()

                    Channels.newChannel(URL(providerVersionMappings).openStream()).use { cin ->
                        FileOutputStream(providerVersionsPath.toFile()).use { fout: FileOutputStream ->
                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                        }
                    }

                    ObjectMapper().readValue<Map<String, String>>(providerVersionsPath.toFile())
                }[it] ?: throw IllegalArgumentException("Failed to find version provider for version '$it'"),
                includeScopes = setOf("compile", "runtime", "import"),
            )
        }

        val provider = handler.get(version, type.settingsProvider(repository))

        minecraftHandler = MinecraftHandler(
            version,
            cachePath,
            provider,
            mcArgs.split(';').toTypedArray()
        )
    }

    override fun onDisable() {
    }
}