package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.Resource
import dev.extframework.launchermeta.handler.*
import dev.extframework.minecraft.bootstrapper.MinecraftArtifactRequest
import dev.extframework.minecraft.bootstrapper.MinecraftDescriptor
import dev.extframework.minecraft.bootstrapper.MinecraftLibDescriptor
import dev.extframework.minecraft.bootstrapper.MinecraftRepositorySettings

public data class MinecraftArtifactMetadata(
    override val descriptor: MinecraftDescriptor,

    val mcJar: Resource,
    val launchMetadata: LaunchMetadata,

    override val parents: List<Nothing>
) : ArtifactMetadata<MinecraftDescriptor, Nothing>(
    descriptor, parents
)

public class MinecraftArtifactRepository :
    ArtifactRepository<MinecraftRepositorySettings, MinecraftArtifactRequest, MinecraftArtifactMetadata> {
    override val factory: MinecraftRepositoryFactory = MinecraftRepositoryFactory
    override val name: String = "minecraft"
    override val settings: MinecraftRepositorySettings = MinecraftRepositorySettings

    override fun get(request: MinecraftArtifactRequest): AsyncJob<MinecraftArtifactMetadata> = asyncJob() {
        val manifest = loadVersionManifest().find(request.descriptor.version)
            ?: throw IllegalStateException("Failed to find minecraft version: '${request.descriptor.version}'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")

        val metadata = parseMetadata(manifest.metadata().merge()).merge()

        val mcJar = metadata.downloads[LaunchMetadataDownloadType.CLIENT]
            ?.toResource()?.merge()
            ?: throw IllegalArgumentException("Cant find client in launch metadata?")

        MinecraftArtifactMetadata(
            request.descriptor,
            mcJar,
            metadata,
            listOf()
        )
    }
}

public object MinecraftRepositoryFactory : RepositoryFactory<MinecraftRepositorySettings, MinecraftArtifactRepository> {
    override fun createNew(settings: MinecraftRepositorySettings): MinecraftArtifactRepository {
        return MinecraftArtifactRepository()
    }
}

public data class MinecraftLibArtifactMetadata(
    override val descriptor: MinecraftLibDescriptor,

    val library: MetadataLibrary,
    val artifact: McArtifact,
) : ArtifactMetadata<MinecraftLibDescriptor, Nothing>(
    descriptor, listOf()
) {
    val jar: Result<Resource>
        get() = artifact.toResource()

    override val parents: List<Nothing> = listOf()
}

public data class MinecraftLibArtifactRequest(
    override val descriptor: MinecraftLibDescriptor,
    val library: MetadataLibrary,
) : ArtifactRequest<MinecraftLibDescriptor>

public object MinecraftLibArtifactRepository :
    ArtifactRepository<MinecraftRepositorySettings, MinecraftLibArtifactRequest, MinecraftLibArtifactMetadata> {
    override val factory: MinecraftLibRepositoryFactory = MinecraftLibRepositoryFactory
    override val name: String = "minecraft libs"
    override val settings: MinecraftRepositorySettings = MinecraftRepositorySettings
    private val metadataProcessor = DefaultMetadataProcessor()

    override fun get(request: MinecraftLibArtifactRequest): AsyncJob<MinecraftLibArtifactMetadata> = asyncJob {
        MinecraftLibArtifactMetadata(
            request.descriptor,
            request.library,
            metadataProcessor.deriveArtifacts(OsType.type, request.library)
                .firstOrNull() ?: throw MetadataRequestException.MetadataNotFound(
                request.descriptor,
                ".jar",
                Exception("No artifact present in the given metadata library (natives nor artifact)")
            ),
        )
    }
}

public object MinecraftLibRepositoryFactory :
    RepositoryFactory<MinecraftRepositorySettings, MinecraftLibArtifactRepository> {
    override fun createNew(settings: MinecraftRepositorySettings): MinecraftLibArtifactRepository {
        return MinecraftLibArtifactRepository
    }
}