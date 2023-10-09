package net.yakclient.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.jobCatching
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.jobs.progress.status
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.archives.*
import net.yakclient.archives.zip.ZipResolutionResult
import net.yakclient.boot.loader.*
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
import java.util.*
import kotlin.io.path.inputStream

public const val MINECRAFT_RESOURCES_URL: String = "https://resources.download.minecraft.net"

public data class DefaultMinecraftReference(
    override val version: String,
    override val archive: ArchiveReference,
    override val libraries: List<ArchiveReference>,
    val manifest: LaunchMetadata,
    override val mappings: ArchiveMapping,
    override val runtimeInfo: MinecraftReference.GameRuntimeInfo
) : MinecraftReference

internal fun loadMinecraft(
    reference: DefaultMinecraftReference,
    parent: ClassLoader
): Triple<ArchiveHandle, List<ArchiveHandle>, LaunchMetadata> {
    val (_, mcReference, minecraftDependencies: List<ArchiveReference>, manifest) = reference

    val dependenciesLoader = IntegratedLoader(
        sp = DelegatingSourceProvider(minecraftDependencies.map(::ArchiveSourceProvider)),
        parent = parent
    )

    val minecraftLibraries = Archives.resolve(
        minecraftDependencies,
        Archives.Resolvers.ZIP_RESOLVER,
    ) {
        dependenciesLoader
    }.map(ZipResolutionResult::archive)

    val mcLoader = IntegratedLoader(
        sp = ArchiveSourceProvider(
            mcReference
        ),
        cp = DelegatingClassProvider(minecraftLibraries.map(::ArchiveClassProvider)),
        parent = parent
    )

    // Resolves reference
    val minecraft = Archives.resolve(
        mcReference,
        mcLoader,
        Archives.Resolvers.ZIP_RESOLVER,
        minecraftLibraries.toSet(),
    )

    return Triple(minecraft.archive, minecraftLibraries, manifest)
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
                            ExternalResource(
                                URI.create("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}"),
                                HexFormat.of().parseHex(asset.checksum),
                                "SHA1"
                            ) copyTo assetPath

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
            libraries.map { lib ->
                async {
                    val desc = SimpleMavenDescriptor.parseDescription(lib.name)!!
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

    // Loads minecraft reference
    val mcReference = Archives.find(minecraftPath, Archives.Finders.ZIP_FINDER)
    DefaultMinecraftReference(
        mcVersion,
        mcReference,
        minecraftDependencies.attempt().awaitAll(),
        metadata,
        ProGuardMappingParser.parse(mappingsPath.inputStream()),
        MinecraftReference.GameRuntimeInfo(
            assetsPath,
            metadata.assetIndex.id,
            versionPath
        )
    )
}