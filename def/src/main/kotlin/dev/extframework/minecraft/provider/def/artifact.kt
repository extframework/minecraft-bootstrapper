package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.launchermeta.handler.*
import dev.extframework.minecraft.bootstrapper.MinecraftArtifactRequest
import dev.extframework.minecraft.bootstrapper.MinecraftDescriptor
import dev.extframework.minecraft.bootstrapper.MinecraftRepositorySettings

public data class MinecraftArtifactMetadata(
    override val descriptor: MinecraftDescriptor,

    val mcJar: Resource,
    val launchMetadata: LaunchMetadata,
    val mappings: Resource,

    override val parents: List<Nothing>
) : ArtifactMetadata<MinecraftDescriptor, Nothing>(
    descriptor, parents
)

public class MinecraftArtifactRepository :
    ArtifactRepository<MinecraftRepositorySettings, MinecraftArtifactRequest, MinecraftArtifactMetadata> {
    override val factory: MinecraftRepositoryFactory = MinecraftRepositoryFactory
    override val name: String = "minecraft"
    override val settings: MinecraftRepositorySettings = MinecraftRepositorySettings

    override fun get(request: MinecraftArtifactRequest): Job<MinecraftArtifactMetadata> = job {
        val manifest = loadVersionManifest().find(request.descriptor.version)
            ?: throw IllegalStateException("Failed to find minecraft version: '${request.descriptor.version}'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")

        val metadata = parseMetadata(manifest.metadata().merge()).merge()

        val mcJar = metadata.downloads[LaunchMetadataDownloadType.CLIENT]
            ?.toResource()?.merge()
            ?: throw IllegalArgumentException("Cant find client in launch metadata?")

        val mappings = metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]
            ?.toResource()?.merge()
            ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")

        MinecraftArtifactMetadata(
            request.descriptor,
            mcJar,
            metadata,
            mappings,
            listOf()
        )
    }
}

public object MinecraftRepositoryFactory : RepositoryFactory<MinecraftRepositorySettings, MinecraftArtifactRepository> {
    override fun createNew(settings: MinecraftRepositorySettings): MinecraftArtifactRepository {
        return MinecraftArtifactRepository()
    }
}