package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.*
import dev.extframework.boot.loader.ArchiveResourceProvider
import dev.extframework.boot.loader.DelegatingResourceProvider
import dev.extframework.boot.loader.ResourceProvider
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.DefaultMetadataProcessor
import dev.extframework.launchermeta.handler.LaunchMetadata
import dev.extframework.launchermeta.handler.OsType
import dev.extframework.minecraft.bootstrapper.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.concurrent.Executors

public const val MINECRAFT_RESOURCES_URL: String = "https://resources.download.minecraft.net"

public class DefaultMinecraftResolver internal constructor(
    private var basePath: Path,
    private val libResolver: MinecraftLibResolver,
) : MinecraftResolver<MinecraftArtifactMetadata> {
    override val metadataType: Class<MinecraftArtifactMetadata> = MinecraftArtifactMetadata::class.java
    override val name: String = "minecraft"
    override val nodeType: Class<in MinecraftNode> = MinecraftNode::class.java
    private val assetsPool = Executors.newWorkStealingPool()

    init {
        basePath = basePath.toAbsolutePath()
    }

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<MinecraftDescriptor> = result {
        MinecraftDescriptor(
            descriptor.requireKeyInDescriptor("minecraft-version") { trace }
        )
    }

    override fun serializeDescriptor(descriptor: MinecraftDescriptor): Map<String, String> {
        return mapOf(
            "minecraft-version" to descriptor.version
        )
    }

    private fun pathForVersion(version: String) =
        basePath resolve "minecraft" resolve version

    override fun pathForDescriptor(descriptor: MinecraftDescriptor, classifier: String, type: String): Path {
        return pathForVersion(
            descriptor.version
        ) resolve "minecraft-${descriptor.version}-$classifier.$type"
    }

    override fun createContext(settings: MinecraftRepositorySettings): ResolutionContext<MinecraftRepositorySettings, MinecraftArtifactRequest, MinecraftArtifactMetadata> {
        return MinecraftRepositoryFactory.createContext(settings)
    }

    override fun cache(
        artifact: Artifact<MinecraftArtifactMetadata>,
        helper: CacheHelper<MinecraftDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource("mappings.txt", artifact.metadata.mappings)
        helper.withResource("launchmeta.json", Resource("<heap>") {
            ByteArrayInputStream(jacksonObjectMapper().writeValueAsBytes(artifact.metadata.launchMetadata))
        })
        helper.withResource("minecraft.jar", artifact.metadata.mcJar)

        val assetsPath = pathForVersion(artifact.metadata.descriptor.version) resolve "assets"

        val assetsIndexesPath = assetsPath resolve "indexes"
        val assetsObjectsPath = assetsPath resolve "objects"
        val assetIndexCachePath = assetsIndexesPath resolve "${artifact.metadata.launchMetadata.assetIndex.id}.json"

        downloadAssets(
            artifact.metadata.launchMetadata,
            assetsObjectsPath,
            assetIndexCachePath,
            assetsPool.asCoroutineDispatcher(),
        ).awaitAll()

        val libraries = DefaultMetadataProcessor()
            .deriveDependencies(OsType.type, artifact.metadata.launchMetadata)

        val parents = helper.loadLibs(
            libraries,
            libResolver
        )().merge()

        helper.newData(
            artifact.metadata.descriptor,
            parents
        )
    }

    override fun load(
        data: ArchiveData<MinecraftDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<MinecraftNode> = job {
        val ref = Archives.Finders.ZIP_FINDER.find(data.resources["minecraft.jar"]!!.path)

        val metadata = jacksonObjectMapper().readValue<LaunchMetadata>(
            data.resources["launchmeta.json"]!!.path.toFile()
        )
        val mappings = data.resources["mappings.txt"]!!.path

        MinecraftNode(
            data.descriptor,
            accessTree,
            DelegatingResourceProvider(
                accessTree.targets
                    .map(ArchiveTarget::relationship)
                    .map(ArchiveRelationship::node)
                    .filterIsInstance<MinecraftLibNode>()
                    .map(MinecraftLibNode::resources) + ArchiveResourceProvider(ref)
            ),
            mappings,
            MinecraftNode.GameRuntimeInfo(
                metadata.mainClass,
                pathForVersion(data.descriptor.version) resolve "assets",
                metadata.assetIndex.id,
                pathForVersion(data.descriptor.version)
            )
        )
    }
}

public data class MinecraftLibNode(
    override val descriptor: SimpleMavenDescriptor,
    val resources: ResourceProvider
) : ArchiveNode<SimpleMavenDescriptor> {
    override val access: ArchiveAccessTree = object : ArchiveAccessTree {
        override val descriptor: ArtifactMetadata.Descriptor = this@MinecraftLibNode.descriptor
        override val targets: List<ArchiveTarget> = listOf()
    }
}

public class MinecraftLibResolver : MavenLikeResolver<MinecraftLibNode, SimpleMavenArtifactMetadata> {
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "minecraft-libs"
    override val nodeType: Class<in MinecraftLibNode> = MinecraftLibNode::class.java

    override fun cache(
        artifact: Artifact<SimpleMavenArtifactMetadata>,
        helper: CacheHelper<SimpleMavenDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource("jar.jar", artifact.metadata.resource)

        helper.newData(
            artifact.metadata.descriptor,
            artifact.parents.mapAsync {
                helper.cache(
                    it, this@MinecraftLibResolver,
                )().merge()
            }.awaitAll()
        )
    }

    override fun createContext(
        settings: SimpleMavenRepositorySettings
    ): ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata> {
        return MinecraftLibResolutionContext()
    }

    override fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<MinecraftLibNode> = job {
        val archive = data.resources["jar.jar"]!!.let {
            Archives.find(it.path, Archives.Finders.ZIP_FINDER)
        }

        MinecraftLibNode(
            data.descriptor,
            ArchiveResourceProvider(archive)
        )
    }
}

internal class MinecraftLibResolutionContext : ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata>(
    SimpleMaven.createNew(minecraftRepo)
) {
    override fun getAndResolveAsync(
        request: SimpleMavenArtifactRequest
    ): AsyncJob<Artifact<SimpleMavenArtifactMetadata>> = asyncJob {
        Artifact(
            SimpleMavenArtifactMetadata(
                request.descriptor,
                repository.settings.layout.resourceOf(
                    request.descriptor.group,
                    request.descriptor.artifact,
                    request.descriptor.version,
                    null,
                    "jar"
                )().merge(), listOf()
            ),
            listOf()
        )
    }
}