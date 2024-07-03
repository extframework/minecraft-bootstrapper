package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap

private const val PROPERTY_FILE_LOCATION = "META-INF/minecraft-provider.properties"

private const val MINECRAFT_PROVIDER_CN = "provider-name"

//public class MinecraftHandlerDependencyResolver(
//    internal val repository: SimpleMavenRepositorySettings,
//) : MavenDependencyResolver(
//    parentClassLoader = MinecraftBootstrapper::class.java.classLoader,
//)

internal class MinecraftProviderFinder(
    cachePath: Path
) {
    private val info: ProviderVersionInfo

    private data class ProviderVersionInfo(
        val default: String,
        val overrides: Map<String, String> = HashMap()
    )

    init {
        val path = cachePath resolve "mc-provider-version-info.json"

        if (path.make()) Channels.newChannel(URL("https://static.extframework.dev/mc-provider-version-info.json").openStream())
            .use { cin ->
                FileOutputStream(path.toFile()).use { fout: FileOutputStream ->
                    fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                }
            }

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        info = mapper.readValue<ProviderVersionInfo>(path.toFile())
    }

    fun find(
        version: String
    ) = SimpleMavenDescriptor.parseDescription(info.overrides[version] ?: info.default)
        ?: throw Exception("Invalid Minecraft Provider Version data specified by: 'https://static.extframework.dev/mc-provider-version-info.json' (or modified by the local user?) Maven descriptors failed to parse.")

}


public fun ArchiveGraph.loadProvider(
    descriptor: SimpleMavenDescriptor,
    resolver: MavenLikeResolver<*, *>,
    repository: SimpleMavenRepositorySettings,
): Job<MinecraftProvider<*>> = job(JobName("Load minecraft provider: '${descriptor.name}'")) {
    cache(
        SimpleMavenArtifactRequest(descriptor, includeScopes = setOf("compile", "runtime", "import")),
        repository,
        resolver
    )().merge()
    val archive = get(descriptor, resolver)().merge().archive
        ?: throw ArchiveException(
            ArchiveTrace(descriptor, null),
            "Minecraft provider has no archive! ('${descriptor}')",
        )

    val properties =
        checkNotNull(archive.classloader.getResourceAsStream(PROPERTY_FILE_LOCATION)) { "Failed to find Minecraft Provider properties file in given archive." }.use {
            Properties().apply { load(it) }
        }

    val providerClassName = properties.getProperty(MINECRAFT_PROVIDER_CN)
        ?: throw IllegalStateException("Invalid minecraft-provider app class name.")

    val clazz = archive.classloader.loadClass(providerClassName)

    clazz.getConstructor().newInstance() as? MinecraftProvider<*>
        ?: throw IllegalStateException("Loaded provider class, but type is not a MinecraftProvider!")
}

public data class MinecraftProviderRequest(
    override val descriptor: MinecraftProviderDescriptor,
) : ArtifactRequest<MinecraftProviderDescriptor>

public data class MinecraftProviderDescriptor(
    val version: String,
) : ArtifactMetadata.Descriptor {
    override val name: String by ::version
}