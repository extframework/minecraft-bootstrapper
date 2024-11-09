package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm

public data class MinecraftArtifactRequest(
    override val descriptor: MinecraftDescriptor
) : ArtifactRequest<MinecraftDescriptor>

public data class MinecraftDescriptor(
    val version: String
) : ArtifactMetadata.Descriptor {
    override val name: String = "minecraft $version"
}

public val minecraftRepo: SimpleMavenRepositorySettings = SimpleMavenRepositorySettings.default(
    url = "https://libraries.minecraft.net",
    requireResourceVerification = false
)

public object MinecraftRepositorySettings : SimpleMavenRepositorySettings(
    SimpleMavenDefaultLayout(
        "https://libraries.minecraft.net",
        ResourceAlgorithm.SHA1,
        releasesEnabled = true,
        snapshotsEnabled = true,
        requireResourceVerification = true
    ),
    ResourceAlgorithm.SHA1,
    requireResourceVerification = true
)

public typealias MinecraftLibDescriptor = SimpleMavenDescriptor



//public data class MinecraftLibDescriptor(
//    val resource: Resource,
//    val path: Path,
//    override val name: String
//) : ArtifactMetadata.Descriptor
