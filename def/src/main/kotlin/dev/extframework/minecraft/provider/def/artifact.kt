package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.RepositoryStubResolutionException
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.ResourceAlgorithm

internal val mcRepo =
    SimpleMavenRepositorySettings.default(
        url = "https://libraries.minecraft.net",
        preferredHash = ResourceAlgorithm.SHA1
    )

internal fun SimpleMavenDescriptor.isMinecraft(): Boolean {
    return group == "net.minecraft" && artifact == "minecraft"
}

public class MinecraftMetadataHandler(
    settings: SimpleMavenRepositorySettings,
    private val reference: DefaultMinecraftReference,
) : SimpleMavenMetadataHandler(settings) {
    override fun requestMetadata(desc: SimpleMavenDescriptor): Job<SimpleMavenArtifactMetadata> =
        job {
            if (desc.isMinecraft()) {
                SimpleMavenArtifactMetadata(
                    desc,
                    null,
                    reference.libraryDescriptors.map {
                        SimpleMavenChildInfo(
                            it.key,
                            listOf(
                                SimpleMavenRepositoryStub(
                                    PomRepository(
                                        null,
                                        "minecraft",
                                        ""
                                    ),
                                    false
                                )
                            ),
                            "mclib"
                        )
                    }
                )
            } else if (reference.libraryDescriptors.contains(desc)) {
                SimpleMavenArtifactMetadata(
                    desc,
                    null,
                    listOf()
                )
            } else throw MetadataRequestException.MetadataNotFound(
                desc,
                "minecraft libraries"
            )
        }
}

public class MinecraftArtifactRepository(
    factory: MinecraftRepositoryFactory,
    settings: SimpleMavenRepositorySettings,
    reference: DefaultMinecraftReference,
) : SimpleMavenArtifactRepository(
    factory,
    MinecraftMetadataHandler(settings, reference),
    settings
) {
    override val stubResolver: SimpleMavenArtifactStubResolver = SimpleMavenArtifactStubResolver(
        { stub ->
            result {
                if (stub.unresolvedRepository.name == "minecraft") {
                    mcRepo
                } else throw RepositoryStubResolutionException("Repository not minecraft repository")
            }
        },
        factory
    )
}

public class MinecraftRepositoryFactory(
    private val reference: DefaultMinecraftReference,
) : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return MinecraftArtifactRepository(this, settings, reference)
    }
}