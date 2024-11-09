package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
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
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.mapOfNonNullValues
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.DefaultMetadataProcessor
import dev.extframework.launchermeta.handler.LaunchMetadata
import dev.extframework.launchermeta.handler.MetadataLibrary
import dev.extframework.launchermeta.handler.OsType
import dev.extframework.minecraft.bootstrapper.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
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
        basePath resolve "versions" resolve version

    override fun pathForDescriptor(descriptor: MinecraftDescriptor, classifier: String, type: String): Path {
        return pathForVersion(
            descriptor.version
        ) resolve "${descriptor.version}${
            classifier
                .takeUnless { it.startsWith("#MC") }
                ?.let { "-$it" }
                ?: ""
        }.$type"
    }

    override fun createContext(settings: MinecraftRepositorySettings): ResolutionContext<MinecraftRepositorySettings, MinecraftArtifactRequest, MinecraftArtifactMetadata> {
        return MinecraftRepositoryFactory.createContext(settings)
    }

    override fun cache(
        artifact: Artifact<MinecraftArtifactMetadata>,
        helper: CacheHelper<MinecraftDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource("#MC launchmeta.json", Resource("<heap>") {
            ByteArrayInputStream(jacksonObjectMapper().writeValueAsBytes(artifact.metadata.launchMetadata))
        })
        helper.withResource("#MC minecraft.jar", artifact.metadata.mcJar)

        val assetsPath = basePath resolve "assets"

        val assetsIndexesPath = assetsPath resolve "indexes"
        val assetsObjectsPath = assetsPath resolve "objects"
        val assetIndexCachePath = assetsIndexesPath resolve "${artifact.metadata.launchMetadata.assetIndex.id}.json"

        downloadAssets(
            artifact.metadata.launchMetadata,
            assetsObjectsPath,
            assetIndexCachePath,
            assetsPool.asCoroutineDispatcher(),
        ).awaitAll()

        val metadataProcessor = DefaultMetadataProcessor()
        val libraries = metadataProcessor
            .deriveDependencies(OsType.type, artifact.metadata.launchMetadata)

        val parents = helper.cacheLibs(
            libraries,
            metadataProcessor,
            libResolver,
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
        val ref = Archives.Finders.ZIP_FINDER.find(data.resources["#MC minecraft.jar"]!!.path)

        val metadata = jacksonObjectMapper().readValue<LaunchMetadata>(
            data.resources["#MC launchmeta.json"]!!.path.toFile()
        )

        MinecraftNode(
            data.descriptor,
            accessTree,
            ref,
            MinecraftNode.GameRuntimeInfo(
                metadata.mainClass,
                basePath resolve "assets",
                metadata.assetIndex.id,
                basePath,
                libResolver.extractPath,
            )
        )
    }
}

public class MinecraftLibResolver(
    public val extractPath: Path,
) : ArchiveNodeResolver<MinecraftLibDescriptor, MinecraftLibArtifactRequest, MinecraftLibNode, MinecraftRepositorySettings, MinecraftLibArtifactMetadata> {
    override val metadataType: Class<MinecraftLibArtifactMetadata> = MinecraftLibArtifactMetadata::class.java
    override val name: String = "minecraft-libs"
    override val nodeType: Class<in MinecraftLibNode> = MinecraftLibNode::class.java
    private val mapper = jacksonObjectMapper()

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<SimpleMavenDescriptor> = result {
        SimpleMavenDescriptor(
            descriptor.requireKeyInDescriptor("group") { trace },
            descriptor.requireKeyInDescriptor("artifact") { trace },
            descriptor.requireKeyInDescriptor("version") { trace },
            descriptor["classifier"]
        )
    }

    override fun pathForDescriptor(descriptor: MinecraftLibDescriptor, classifier: String, type: String): Path {
        return Paths.get(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            descriptor.classifier ?: "",
            "${descriptor.artifact}-${descriptor.version}-$classifier.$type"
        )
    }

    override fun serializeDescriptor(descriptor: SimpleMavenDescriptor): Map<String, String> {
        return mapOfNonNullValues(
            "group" to descriptor.group,
            "artifact" to descriptor.artifact,
            "version" to descriptor.version,
            "classifier" to descriptor.classifier
        )
    }

    override fun cache(
        artifact: Artifact<MinecraftLibArtifactMetadata>,
        helper: CacheHelper<MinecraftLibDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource("jar.jar", artifact.metadata.jar.merge())
        helper.withResource("lib.json", Resource("<heap>") {
            ByteArrayInputStream(mapper.writeValueAsBytes(artifact.metadata.library))
        })

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
        settings: MinecraftRepositorySettings,
    ): ResolutionContext<MinecraftRepositorySettings, MinecraftLibArtifactRequest, MinecraftLibArtifactMetadata> {
        return MinecraftLibRepositoryFactory.createContext(settings)
    }

    override fun load(
        data: ArchiveData<MinecraftLibDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<MinecraftLibNode> = job {
        val archive = data.resources["jar.jar"]!!.let {
            Archives.find(it.path, Archives.Finders.ZIP_FINDER)
        }
        val library = data.resources["lib.json"]!!.let {
            mapper.readValue<MetadataLibrary>(it.path.toFile())
        }

        val extract = library.extract
        if (extract != null) {
            archive.reader.entries()
                .filterNot { entry ->
                    extract.exclude.any { exclude ->
                        entry.name.startsWith(exclude)
                    }
                }.forEach {
                    val path = extractPath resolve it.name

                    if (path.make()) {
                        it.resource.copyTo(path)
                    }
                }
        }

        MinecraftLibNode(
            data.descriptor,
            archive,
            object : ArchiveAccessTree {
                override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
                override val targets: List<ArchiveTarget> = listOf()
            }
        )
    }
}