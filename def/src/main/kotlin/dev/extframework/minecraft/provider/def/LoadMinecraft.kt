package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.*
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.loader.ArchiveResourceProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.MutableClassLoader
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.store.DataStore
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.*
import dev.extframework.minecraft.bootstrapper.MinecraftHandle
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.*
import kotlin.math.floor

public const val MINECRAFT_RESOURCES_URL: String = "https://resources.download.minecraft.net"

public data class DefaultMinecraftReference(
    val version: String,
    val archive: ArchiveReference,
    val libraries: List<ArchiveReference>,
    val manifest: LaunchMetadata,
    val mappings: Path,
    val runtimeInfo: MinecraftHandle.GameRuntimeInfo,
    internal val libraryDescriptors: Map<SimpleMavenDescriptor, ArchiveReference>
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
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "minecraft"

    private val factory = MinecraftRepositoryFactory(reference)

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

    runBlocking(Dispatchers.IO) {
        // Download minecraft
        val downloadMc = if (minecraftPath.make()) async {
            downloadJar(metadata, minecraftPath)
        } else null

        // Download mappings
        val downloadMappings =
            if (mappingsPath.make()) async {
                downloadMappings(metadata, mappingsPath)
            } else null

        val assetIndexCachePath = assetsIndexesPath resolve "${metadata.assetIndex.id}.json"
        val downloadAssets = if (assetIndexCachePath.make()) {
            downloadAssets(metadata, assetsObjectsPath, assetIndexCachePath)
        } else null

        val libPath = versionPath resolve "lib"

        val libraries =
            DefaultMetadataProcessor().deriveDependencies(OsType.type, metadata)

        // Loads minecraft dependencies
        val minecraftDependencies =
            loadLibs(libraries, libPath)

        downloadMc?.await()
        downloadMappings?.await()
        downloadAssets?.awaitAll()

        info("Minecraft assets downloaded.")
        val awaitedDependencyMap = minecraftDependencies.awaitAll().associate { it }

        info("Minecraft references fully loaded.")

        // Loads minecraft reference
        val mcReference = Archives.find(minecraftPath, Archives.Finders.ZIP_FINDER)
        DefaultMinecraftReference(
            mcVersion,
            mcReference,
            awaitedDependencyMap.values.toList(),
            metadata,
            mappingsPath,
            MinecraftHandle.GameRuntimeInfo(
                metadata.mainClass,
                assetsPath,
                metadata.assetIndex.id,
                versionPath,
            ),
            awaitedDependencyMap
        )
    }
}

internal suspend fun JobScope.loadLibs(
    libraries: List<MetadataLibrary>,
    libPath: Path
) = coroutineScope {
    libraries.map { lib ->
        val desc = SimpleMavenDescriptor.parseDescription(lib.name)!!

        async {
            val toPath = libPath resolve (desc.group.replace(
                '.',
                File.separatorChar
            )) resolve desc.artifact resolve desc.version resolve "${desc.artifact}-${desc.version}${if (desc.classifier == null) "" else "-${desc.classifier}"}.jar"

            if (toPath.make()) {
                val resource = lib.downloads.artifact
                    .toResource().merge()
                resource copyTo toPath
            }

            desc to Archives.Finders.ZIP_FINDER.find(toPath)
        }
    }
}

internal suspend fun JobScope.downloadAssets(
    metadata: LaunchMetadata,
    assetsObjectsPath: Path,
    assetIndexCachePath: Path
) = coroutineScope {
    val assetIndexCacheResource = metadata.assetIndex().merge()
    val objects = parseAssetIndex(assetIndexCacheResource).merge().objects
    val totalSize = objects.values.sumOf { it.size }

    var bytesDownloaded = 0L
    val startTime = System.currentTimeMillis()

    val assetPaths = objects
        .entries
        .map { entry ->
            async {
                val (name, asset) = entry
                val assetPath = assetsObjectsPath resolve asset.checksum.take(2) resolve asset.checksum
                if (assetPath.make()) {
                    val predictedTimeLeft =
                        ((System.currentTimeMillis() - startTime) / (bytesDownloaded + 1).toDouble()) * (totalSize - bytesDownloaded)

                    bytesDownloaded += asset.size

                    val unverifiedResource =
                        URL("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}")
                            .toResource()

                    VerifiedResource(
                        unverifiedResource,
                        ResourceAlgorithm.SHA1,
                        HexFormat.of().parseHex(asset.checksum),
                    ) copyTo assetPath

                    info(
                        "Downloaded asset: '$name', ${floor((bytesDownloaded.toDouble() / totalSize) * 100).toInt()}% done. ${
                            convertMillisToTimeSpan(
                                predictedTimeLeft.toLong()
                            )
                        } left, ${convertBytesToPrettyString(bytesDownloaded)}/${
                            convertBytesToPrettyString(
                                totalSize
                            )
                        }"
                    )
                }
            }
        }

    assetIndexCacheResource copyTo assetIndexCachePath

    assetPaths
}

internal fun JobScope.downloadMappings(
    metadata: LaunchMetadata,
    mappingsPath: Path
) {
    val mappingsResource =
        metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]
            ?.toResource()?.merge()
            ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")

    info("Downloading Minecraft mappings")
    mappingsResource copyTo mappingsPath
}

internal fun JobScope.downloadJar(
    metadata: LaunchMetadata,
    minecraftPath: Path
) {
    val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]
        ?.toResource()?.merge()
        ?: throw IllegalArgumentException("Cant find client in launch metadata?")

    info("Downloading the Minecraft jar")
    clientResource copyTo minecraftPath
}

internal fun convertMillisToTimeSpan(millis: Long): String {
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

internal fun convertBytesToPrettyString(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")

    if (bytes == 0L) {
        return "0 B"
    }

    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}