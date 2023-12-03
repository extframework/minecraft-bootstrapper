package net.yakclient.minecraft.provider.def

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomData
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.jobs.progress.status
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.ClassLoaderProvider
import net.yakclient.archives.zip.ZipResolutionResult
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.boot.maven.MavenDependencyResolver
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ExternalResource
import net.yakclient.launchermeta.handler.*
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.inputStream
import kotlin.reflect.KClass

public const val MINECRAFT_RESOURCES_URL: String = "https://resources.download.minecraft.net"

public data class DefaultMinecraftReference(
    override val version: String,
    override val archive: ArchiveReference,
    override val libraries: List<ArchiveReference>,
    val manifest: LaunchMetadata,
    override val mappings: ArchiveMapping,
    override val runtimeInfo: MinecraftReference.GameRuntimeInfo,

    internal val libraryDescriptors: Map<SimpleMavenDescriptor, ArchiveReference>
) : MinecraftReference

// Pretty much just a repeat of DelegatingSourceProvider in boot unfortunately
public class MutableArchiveSourceProvider(
    private val delegates: MutableList<SourceProvider>
) : SourceProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String>
        get() = delegates.flatMapTo(HashSet(), SourceProvider::packages)
    private val packageMap: Map<String, List<SourceProvider>>
        get() = delegates.flatGroupBy { it.packages }

    override fun getSource(name: String): ByteBuffer? =
        packageMap[name.substring(0, name.lastIndexOf('.')
            .let { if (it == -1) 0 else it })]?.firstNotNullOfOrNull { it.getSource(name) }

    override fun getResource(name: String): URL? =
        delegates.firstNotNullOfOrNull { it.getResource(name) }

    override fun getResource(name: String, module: String): URL? =
        delegates.firstNotNullOfOrNull { it.getResource(name, module) }

    public fun add(provider: SourceProvider) {
        delegates.add(provider)
    }

}

private val mcRepo = SimpleMavenRepositorySettings.mavenCentral()

public class MutableClassLoader(
    private val sources: MutableArchiveSourceProvider,
    parent: ClassLoader
) : IntegratedLoader(sp = sources, parent = parent) {
    public fun addSource(archive: ArchiveReference) {
        sources.add(ArchiveSourceProvider(archive))
    }
}

public class MinecraftDependencyResolver(
    private val reference: DefaultMinecraftReference,
    parent: ClassLoader
) : MavenDependencyResolver(ZipResolutionProvider) {
    private val classloader = MutableClassLoader(MutableArchiveSourceProvider(ArrayList()), parent)

    private fun SimpleMavenDescriptor.isMinecraft(): Boolean {
        return group == "net.minecraft" && artifact == "minecraft"
    }

    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>, SimpleMavenArtifactRepository> =
        object :
            RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
            override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
                val f = this
                return object : SimpleMavenArtifactRepository(
                    f,
                    object : SimpleMavenMetadataHandler(settings) {
                        override fun requestMetadata(desc: SimpleMavenDescriptor): arrow.core.Either<MetadataRequestException, SimpleMavenArtifactMetadata> {
                            return if (desc.isMinecraft()) {
                                return SimpleMavenArtifactMetadata(
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
                                                    )
                                                )
                                            ),
                                            "mclib"
                                        )
                                    }
                                ).right()
                            } else if (reference.libraryDescriptors.contains(desc)) {
                                SimpleMavenArtifactMetadata(
                                    desc,
                                    null,
                                    listOf()
                                ).right()
                            } else Either.Left(MetadataRequestException.MetadataNotFound)
                        }
                    },
                    settings
                ) {
                    override val stubResolver: SimpleMavenArtifactStubResolver = SimpleMavenArtifactStubResolver(
                        object : RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> {
                            override fun resolve(stub: SimpleMavenRepositoryStub): Either<RepositoryStubResolutionException, SimpleMavenRepositorySettings> {
                                return if (stub.unresolvedRepository.name == "minecraft") {
                                    return mcRepo.right()
                                } else RepositoryStubResolutionException("Repository not minecraft repository").left()
                            }
                        },
                        f
                    )
                }
            }
        }
    override val metadataType: KClass<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class
    override val name: String = "minecraft"
    override val nodeType: KClass<DependencyNode> = DependencyNode::class
    override suspend fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<DependencyNode, ArchiveException> = jobScope {
        if (data.descriptor.isMinecraft()) {
            classloader.addSource(reference.archive)

            val children = data.children.map {
                resolver.load(it.descriptor as SimpleMavenDescriptor, this@MinecraftDependencyResolver)
            }

            val handle = Archives.resolve(
                reference.archive,
                classloader,
                Archives.Resolvers.ZIP_RESOLVER,
                children.flatMapTo(HashSet()) { it.handleOrChildren() }
            )

            DependencyNode(
                handle.archive,
                children.toSet(),
                data.descriptor
            )
        } else {
            val archive = reference.libraryDescriptors[data.descriptor]
                ?: throw java.lang.IllegalStateException("Unknown minecraft library: '${data.descriptor}'")

            val handle = Archives.resolve(
                archive,
                classloader,
                Archives.Resolvers.ZIP_RESOLVER,
                setOf()
            )

            DependencyNode(
                handle.archive,
                setOf(),
                data.descriptor
            )
        }
    }

//    override suspend fun load(descriptor: SimpleMavenDescriptor): JobResult<DependencyNode, ArchiveException> =
//        job(JobName("Load minecraft library: '$descriptor'")) {
//            if (descriptor.group == "net.minecraft" && descriptor.artifact == "minecraft") {
//                val archiveRef = reference.archive
//
//                val children = reference.libraryDescriptors.mapTo(HashSet()) { load(it.key).attempt() }
//
//                classloader.addSource(archiveRef)
//                val handle = Archives.resolve(
//                    archiveRef,
//                    classloader,
//                    Archives.Resolvers.ZIP_RESOLVER,
//                    children.flatMapTo(HashSet()) { it.handleOrChildren() }
//                )
//
//                DependencyNode(
//                    handle.archive,
//                    children,
//                    descriptor
//                )
//            } else {
//                val archiveRef =
//                    reference.libraryDescriptors[descriptor] ?: fail(ArchiveLoadException.ArtifactNotCached)
//
//                classloader.addSource(archiveRef)
//                val handle = Archives.resolve(
//                    archiveRef,
//                    classloader,
//                    Archives.Resolvers.ZIP_RESOLVER
//                )
//
//                DependencyNode(
//                    handle.archive,
//                    setOf(),
//                    descriptor
//                )
//            }
//        }


//    override suspend fun cache(
//        request: SimpleMavenArtifactRequest,
//        repository: SimpleMavenRepositorySettings
//    ): JobResult<Unit, ArchiveLoadException> {
//        return JobResult.Failure(ArchiveLoadException.IllegalState("Cannot call cache here :("))
//    }
}


internal suspend fun loadMinecraft(
    reference: DefaultMinecraftReference,
    parent: ClassLoader,
    archiveGraph: ArchiveGraph
): JobResult<Triple<ArchiveHandle, List<ArchiveHandle>, LaunchMetadata>, ArchiveException> =
    job(JobName("Load minecraft and dependencies ('${reference.version}')'")) {
//    val (_, mcReference, minecraftDependencies: List<ArchiveReference>, manifest) = reference

//    val dependenciesLoader = IntegratedLoader(
//        sp = DelegatingSourceProvider(minecraftDependencies.map(::ArchiveSourceProvider)),
//        parent = parent
//    )
//
//    val minecraftLibraries = Archives.resolve(
//        minecraftDependencies,
//        Archives.Resolvers.ZIP_RESOLVER,
//    ) {
//        dependenciesLoader
//    }.map(ZipResolutionResult::archive)
//
//    val mcLoader = IntegratedLoader(
//        sp = ArchiveSourceProvider(
//            mcReference
//        ),
//        cp = DelegatingClassProvider(minecraftLibraries.map(::ArchiveClassProvider)),
//        parent = parent
//    )
//
//    // Resolves reference
//    val minecraft = Archives.resolve(
//        mcReference,
//        mcLoader,
//        Archives.Resolvers.ZIP_RESOLVER,
//        minecraftLibraries.toSet(),
//    )
        val mcDesc = SimpleMavenDescriptor.parseDescription("net.minecraft:minecraft:${reference.version}")!!

        val resolver = MinecraftDependencyResolver(reference, parent)
        archiveGraph.cache(
            SimpleMavenArtifactRequest(mcDesc, includeScopes = setOf("mclib")),
            mcRepo,
            resolver
        )
        val minecraft = archiveGraph.get(
            mcDesc,
            resolver
        ).attempt()

        val libraries = minecraft.children.flatMap { it.handleOrChildren() }

        Triple(minecraft.archive!!, libraries, reference.manifest)
    }


internal suspend fun loadMinecraftRef(
    mcVersion: String,
    path: Path,
    store: DataStore<String, LaunchMetadata>,
): JobResult<DefaultMinecraftReference, Throwable> = job(JobName("Load minecraft '$mcVersion' and objects")) {
    val versionPath = path resolve mcVersion
    val minecraftPath = versionPath resolve "minecraft-${mcVersion}.jar"
    val mappingsPath = versionPath resolve "minecraft-mappings-${mcVersion}.txt"
    val assetsPath = versionPath resolve "assets"
    val assetsIndexesPath = assetsPath resolve "indexes"
    val assetsObjectsPath = assetsPath resolve "objects"

    val metadata = store[mcVersion] ?: jobCatching(JobName("Download minecraft version manifest ('$mcVersion')")) {
        val manifest = loadVersionManifest().find(mcVersion)
            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")
        val metadata = parseMetadata(manifest.metadata())

        store.put(mcVersion, metadata)

        metadata
    }.attempt()

    val downloadMc = if (minecraftPath.make()) async {
        jobCatching(JobName("Download minecraft '$mcVersion'")) {
            val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()
                ?: throw IllegalArgumentException("Cant find client in launch metadata?")

            clientResource copyTo minecraftPath
        }
    } else null

    // Download mappings

    val downloadMappings =
        if (mappingsPath.make()) async {
            jobCatching(JobName("Downloading minecraft client mappings")) {
                val mappingsResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]?.toResource()
                    ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")

                mappingsResource copyTo mappingsPath
            }
        } else null

    val assetIndexCachePath = assetsIndexesPath resolve "${metadata.assetIndex.id}.json"
    val downloadAssets = if (assetIndexCachePath.make()) {
        info("Couldn't find $mcVersion assets, downloading them.")

        val assetIndexCacheResource = metadata.assetIndex()
        assetIndexCacheResource copyToBlocking assetIndexCachePath

        val objects = parseAssetIndex(assetIndexCacheResource).objects

        val update = 1f / objects.size

        jobCatching(JobName("Download minecraft $mcVersion assets")) {
            objects
                .map { (name, asset) ->
                    async {
                        val assetPath = assetsObjectsPath resolve asset.checksum.take(2) resolve asset.checksum
                        if (assetPath.make()) {
//                            ExternalResource(
//                                URI.create("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}"),
//                                HexFormat.of().parseHex(asset.checksum),
//                                "SHA1"
//                            ) copyTo assetPath

                            status(update) { "Downloaded asset: '$name'" }
                        }
                    }
                }
        }
    } else null

    val libPath = versionPath resolve "lib"

    val libraries = DefaultMetadataProcessor().deriveDependencies(OsType.type, metadata)

    // Loads minecraft dependencies
    val minecraftDependencies =
        jobCatching(JobName("Load (and download) minecraft libraries")) {
            libraries.associate { lib ->
                val desc = SimpleMavenDescriptor.parseDescription(lib.name)!!

                desc to async {
                    val toPath = libPath resolve (desc.group.replace(
                        '.',
                        File.separatorChar
                    )) resolve desc.artifact resolve desc.version resolve "${desc.artifact}-${desc.version}${if (desc.classifier == null) "" else "-${desc.classifier}"}.jar"

                    if (toPath.make()) {
                        lib.downloads.artifact.toResource() copyTo toPath
                        status(1f / libraries.size) { "Downloaded: '${desc.name}'" }
                    }

                    Archives.Finders.ZIP_FINDER.find(toPath)
                }
            }
        }

    downloadMc?.await()?.attempt()
    downloadMappings?.await()?.attempt()
    downloadAssets?.attempt()?.awaitAll()

    val awaitedDependencyMap = minecraftDependencies.attempt().mapValues { it.value.await() }

    // Loads minecraft reference
    val mcReference = Archives.find(minecraftPath, Archives.Finders.ZIP_FINDER)
    DefaultMinecraftReference(
        mcVersion,
        mcReference,
        awaitedDependencyMap.values.toList(),
        metadata,
        ProGuardMappingParser.parse(mappingsPath.inputStream()),
        MinecraftReference.GameRuntimeInfo(
            assetsPath,
            metadata.assetIndex.id,
            versionPath
        ),
        awaitedDependencyMap
    )
}