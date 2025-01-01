package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

private const val PROPERTY_FILE_LOCATION = "META-INF/minecraft-provider.properties"

private const val MINECRAFT_PROVIDER_CN = "provider-name"

public interface MinecraftProviderFinder {
    public fun find(
        version: String
    ): SimpleMavenDescriptor
}

public class MinecraftProviderRemoteLookup(
    cachePath: Path,
    resource: URL = URL("https://static.extframework.dev/mc-provider-version-info.json")
) : MinecraftProviderFinder {
    private val info: ProviderVersionInfo

    private data class ProviderVersionInfo(
        val default: String,
        val overrides: Map<String, String> = HashMap()
    )

    init {
        val path = cachePath resolve "mc-provider-version-info.json"

        try {
            Channels.newChannel(resource.openStream())
                .use { cin ->
                    path.make()
                    FileOutputStream(path.toFile()).use { fout: FileOutputStream ->
                        fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                    }
                }
        } catch (e: Throwable) {
            if (!path.exists()) {
                throw e
            }
        }

        val mapper = jacksonObjectMapper()
        info = mapper.readValue<ProviderVersionInfo>(path.toFile())
    }

    override fun find(
        version: String
    ): SimpleMavenDescriptor = SimpleMavenDescriptor.parseDescription(info.overrides[version] ?: info.default)
        ?: throw Exception("Invalid Minecraft Provider Version data specified by: 'https://static.extframework.dev/mc-provider-version-info.json' (or modified by the local user?) Maven descriptors failed to parse.")

}


public fun ArchiveGraph.loadProvider(
    descriptor: SimpleMavenDescriptor,
    resolver: MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
    repository: SimpleMavenRepositorySettings,
): Job<MinecraftProvider> = job(JobName("Load minecraft provider: '${descriptor.name}'")) {
    cache(
        SimpleMavenArtifactRequest(descriptor, includeScopes = setOf("compile", "runtime", "import")),
        repository,
        resolver
    )().merge()

    val node = get(descriptor, resolver)().merge()
    val archive = node.handle
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

    clazz.getConstructor().newInstance() as? MinecraftProvider
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