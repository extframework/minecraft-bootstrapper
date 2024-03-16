package net.yakclient.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.loader.ArchiveResourceProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.MutableClassLoader
import net.yakclient.boot.maven.MavenDependencyResolver
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.launchermeta.handler.*
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.*
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
    SimpleMavenRepositorySettings.default(
        url = "https://libraries.minecraft.net",
        preferredHash = ResourceAlgorithm.SHA1
    )


private fun ArchiveNode<*>.handleOrParents(): List<ArchiveHandle> {
    return archive?.let(::listOf) ?: parents.flatMap { it.handleOrParents() }
}

public class MinecraftDependencyResolver(
    private val reference: DefaultMinecraftReference,
    private val classloader: MutableClassLoader
) : MavenDependencyResolver(
    parentClassLoader = classloader.parent,
) {
    private fun SimpleMavenDescriptor.isMinecraft(): Boolean {
        return group == "net.minecraft" && artifact == "minecraft"
    }

    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "minecraft"

    private val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>, SimpleMavenArtifactRepository> =
        object :
            RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
            override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
                val f = this
                return object : SimpleMavenArtifactRepository(
                    f,
                    object : SimpleMavenMetadataHandler(settings) {
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
                                } else throw MetadataRequestException.MetadataNotFound
                            }
                    },
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
                        f
                    )
                }
            }
        }

    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, SimpleMavenArtifactMetadata, *> {
        return factory.createContext(settings)
    }

    override fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<BasicDependencyNode> = job {
        if (data.descriptor.isMinecraft()) {
            classloader.addSources(ArchiveSourceProvider(reference.archive))
            classloader.addResources(ArchiveResourceProvider(reference.archive))

            val parents = data.parents.map {
                helper.load(it.descriptor, this@MinecraftDependencyResolver)
            }

            val acessTree = helper.newAccessTree {
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


internal fun loadMinecraft(
    reference: DefaultMinecraftReference,
    classLoader: MutableClassLoader,
    archiveGraph: ArchiveGraph
): Job<Triple<ArchiveHandle, List<ArchiveHandle>, LaunchMetadata>> =
    job(JobName("Load minecraft and dependencies ('${reference.version}')'")) {
        val mcDesc = SimpleMavenDescriptor.parseDescription("net.minecraft:minecraft:${reference.version}")!!

        val resolver = MinecraftDependencyResolver(reference, classLoader)
        archiveGraph.cache(
            SimpleMavenArtifactRequest(mcDesc, includeScopes = setOf("mclib")),
            mcRepo,
            resolver
        )().merge()
        val minecraft = archiveGraph.get(
            mcDesc,
            resolver
        )().merge()

        val libraries = minecraft.parents.flatMap { it.handleOrParents() }

        Triple(minecraft.archive!!, libraries, reference.manifest)
    }

internal fun loadMinecraftRef(
    mcVersion: String,
    path: Path,
    store: DataStore<String, LaunchMetadata>,
): Job<DefaultMinecraftReference> = job(JobName("Load minecraft '$mcVersion' and objects")) {
    val versionPath = path resolve mcVersion
    val minecraftPath = versionPath resolve "minecraft-${mcVersion}.jar"
    val mappingsPath = versionPath resolve "minecraft-mappings-${mcVersion}.txt"
    val assetsPath = versionPath resolve "assets"
    val assetsIndexesPath = assetsPath resolve "indexes"
    val assetsObjectsPath = assetsPath resolve "objects"

    val metadata = store[mcVersion] ?: job(JobName("Download minecraft version manifest ('$mcVersion')")) {
        val manifest = loadVersionManifest().find(mcVersion)
            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")
        val metadata = parseMetadata(manifest.metadata().merge()).merge()

        store.put(mcVersion, metadata)

        metadata
    }().merge()

    runBlocking {
        val downloadMc = if (minecraftPath.make()) async {
            job(JobName("Download minecraft '$mcVersion'")) {
                val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()?.merge()
                    ?: throw IllegalArgumentException("Cant find client in launch metadata?")

                clientResource copyTo minecraftPath
            }
        } else null

        // Download mappings

        val downloadMappings =
            if (mappingsPath.make()) async {
                job(JobName("Downloading minecraft client mappings")) {
                    val mappingsResource =
                        metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]?.toResource()?.merge()
                            ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")

                    mappingsResource copyTo mappingsPath
                }
            } else null

        val assetIndexCachePath = assetsIndexesPath resolve "${metadata.assetIndex.id}.json"
        val downloadAssets = if (assetIndexCachePath.make()) {
            info("Couldn't find $mcVersion assets, downloading them.")

            val assetIndexCacheResource = metadata.assetIndex().merge()

            val objects = parseAssetIndex(assetIndexCacheResource).merge().objects

            job(JobName("Download minecraft $mcVersion assets")) {
                var index = 0
                var startTime = System.currentTimeMillis()

                val assetPaths = objects
                    .entries
                    .map { entry ->
                        val (name, asset) = entry
//                        async {
                        val assetPath = assetsObjectsPath resolve asset.checksum.take(2) resolve asset.checksum
                        if (assetPath.make()) {
                            // TODO redo this to make use of the file size of all assets (is metadata that launchermeta-handler should deal with)
                            val predictedTimeLeft =
                                ((System.currentTimeMillis() - startTime) / (index + 1)) * (objects.size - index)

                            info(
                                "Downloading asset: '$name', ${floor(((index++ + 1).toFloat() / objects.size) * 100).toInt()}% done. ${
                                    convertMillisToTimeSpan(
                                        predictedTimeLeft
                                    )
                                } left"
                            )

                            val unverifiedResource =
                                URL("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}")
                                    .toResource()

                            VerifiedResource(
                                unverifiedResource,
                                ResourceAlgorithm.SHA1,
                                HexFormat.of().parseHex(asset.checksum),
                            ) copyTo assetPath
                        }
//
//                        }
                    }

                assetIndexCacheResource copyTo assetIndexCachePath

                assetPaths
            }
        } else null

        val libPath = versionPath resolve "lib"

        val libraries = DefaultMetadataProcessor().deriveDependencies(OsType.type, metadata)

        // Loads minecraft dependencies
        val minecraftDependencies =
            job(JobName("Load (and download) minecraft libraries")) {
                libraries.associate { lib ->
                    val desc = SimpleMavenDescriptor.parseDescription(lib.name)!!

                    desc to async {
                        val toPath = libPath resolve (desc.group.replace(
                            '.',
                            File.separatorChar
                        )) resolve desc.artifact resolve desc.version resolve "${desc.artifact}-${desc.version}${if (desc.classifier == null) "" else "-${desc.classifier}"}.jar"

                        if (toPath.make()) {
                            lib.downloads.artifact.toResource().merge() copyTo toPath
//                        status(1f / libraries.size) { "Downloaded: '${desc.name}'" }
                        }

                        Archives.Finders.ZIP_FINDER.find(toPath)
                    }
                }
            }

        downloadMc?.await()?.invoke()?.merge()
        downloadMappings?.await()?.invoke()?.merge()
        downloadAssets?.invoke()?.merge()

        val awaitedDependencyMap = minecraftDependencies().merge().mapValues { it.value.await() }

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
}


private fun convertMillisToTimeSpan(millis: Long): String {
    var remainingMillis = millis

    val days = remainingMillis / (24 * 60 * 60 * 1000)
    remainingMillis %= (24 * 60 * 60 * 1000)

    val hours = remainingMillis / (60 * 60 * 1000)
    remainingMillis %= (60 * 60 * 1000)

    val minutes = remainingMillis / (60 * 1000)
    remainingMillis %= (60 * 1000)

    val seconds = remainingMillis / 1000

    return listOf(
        days to "day",
        hours to "hour",
        minutes to "minute",
        seconds to "second"
    ).filter {
        it.first != 0L
    }.joinToString(separator = ", ") { (value, unit) ->
        "$value $unit${if (value != 1L) "s" else ""}"
    }.takeIf { it.isNotEmpty() } ?: "0 seconds"
}
