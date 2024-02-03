package net.yakclient.minecraft.provider.def

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.info
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.loader.*
import net.yakclient.boot.maven.MavenDependencyResolver
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ExternalResource
import net.yakclient.launchermeta.handler.*
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.HexFormat
import kotlin.collections.HashSet
import kotlin.math.floor

public const val MINECRAFT_RESOURCES_URL: String = "https://resources.download.minecraft.net"

public data class DefaultMinecraftReference(
    override val version: String,
    override val archive: ArchiveReference,
    override val libraries: List<ArchiveReference>,
    val manifest: LaunchMetadata,
    override val mappings: Path,
    override val runtimeInfo: MinecraftReference.GameRuntimeInfo,
    internal val libraryDescriptors: Map<SimpleMavenDescriptor, ArchiveReference>
) : MinecraftReference

private val mcRepo =
    SimpleMavenRepositorySettings.default(url = "https://libraries.minecraft.net", preferredHash = HashType.SHA1)


private fun ArchiveNode<*>.handleOrParents(): List<ArchiveHandle> {
    return archive?.let(::listOf) ?: parents.flatMap { it.handleOrParents() }
}

public class MinecraftDependencyResolver(
    private val reference: DefaultMinecraftReference,
    private val classloader: MutableClassLoader
) : MavenDependencyResolver(
    parentClassLoader = classloader.parent,
//    parentPrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges())
) {
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
                        override fun requestMetadata(desc: SimpleMavenDescriptor): Either<MetadataRequestException, SimpleMavenArtifactMetadata> {
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

    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "minecraft"

    override suspend fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): JobResult<BasicDependencyNode, ArchiveException> = jobScope {
        if (data.descriptor.isMinecraft()) {
            classloader.addSources(ArchiveSourceProvider(reference.archive))
            classloader.addResources(ArchiveResourceProvider(reference.archive))

            val parents = data.parents.map {
                helper.load(it.descriptor, this@MinecraftDependencyResolver)
            }

            val acessTree =helper.newAccessTree {
                allDirect(parents)
            }


            val handle = Archives.resolve(
                reference.archive,
                classloader,
                Archives.Resolvers.ZIP_RESOLVER,
                parents.flatMapTo(HashSet()) { it.handleOrParents() }
            )

            BasicDependencyNode(
                data.descriptor,
                handle.archive,
                parents.toSet(),
                acessTree,
                this@MinecraftDependencyResolver,
            )
        } else {
            val archive = reference.libraryDescriptors[data.descriptor]
                ?: throw java.lang.IllegalStateException("Unknown minecraft library: '${data.descriptor}'")

            classloader.addSources(ArchiveSourceProvider(archive))
            classloader.addResources(ArchiveResourceProvider(archive))

            val accessTree = helper.newAccessTree {
                // TODO not correct
            }

            val handle = Archives.resolve(
                archive,
                classloader,
                Archives.Resolvers.ZIP_RESOLVER,
                setOf()
            )

            BasicDependencyNode(
                data.descriptor,
                handle.archive,
                setOf(),
                accessTree,
                this@MinecraftDependencyResolver
            )
        }
    }
}


internal suspend fun loadMinecraft(
    reference: DefaultMinecraftReference,
    classLoader: MutableClassLoader,
    archiveGraph: ArchiveGraph
): JobResult<Triple<ArchiveHandle, List<ArchiveHandle>, LaunchMetadata>, ArchiveException> =
    job(JobName("Load minecraft and dependencies ('${reference.version}')'")) {
        val mcDesc = SimpleMavenDescriptor.parseDescription("net.minecraft:minecraft:${reference.version}")!!

        val resolver = MinecraftDependencyResolver(reference, classLoader)
        archiveGraph.cache(
            SimpleMavenArtifactRequest(mcDesc, includeScopes = setOf("mclib")),
            mcRepo,
            resolver
        )
        val minecraft = archiveGraph.get(
            mcDesc,
            resolver
        ).attempt()

        val libraries = minecraft.parents.flatMap { it.handleOrParents() }

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



        jobCatching(JobName("Download minecraft $mcVersion assets")) {
            objects
                .entries
                .withIndex()
                .map { (index, entry) ->
                    val (name, asset) = entry
                    async {
                        val assetPath = assetsObjectsPath resolve asset.checksum.take(2) resolve asset.checksum
                        if (assetPath.make()) {
                            info("Downloading asset: '$name', ${floor(((index + 1).toFloat() / objects.size) * 100).toInt()}% done.")

                            ExternalResource(
                                URI.create("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}"),
                                HexFormat.of().parseHex(asset.checksum),
                                "SHA1"
                            ) copyTo assetPath

//                            status(update) { "Downloaded asset: '$name'" }
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
//                        status(1f / libraries.size) { "Downloaded: '${desc.name}'" }
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
        mappingsPath,
        MinecraftReference.GameRuntimeInfo(
            assetsPath,
            metadata.assetIndex.id,
            versionPath
        ),
        awaitedDependencyMap
    )
}