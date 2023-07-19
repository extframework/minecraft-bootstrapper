package net.yakclient.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingSourceProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ExternalResource
import net.yakclient.launchermeta.handler.*
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.HexFormat
import java.util.logging.Level
import java.util.logging.Logger
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
): Pair<ArchiveHandle, LaunchMetadata> {
    val (_, mcReference, minecraftDependencies, manifest) = reference

    val mcLoader = IntegratedLoader(
        sp = DelegatingSourceProvider(
            minecraftDependencies.map(::ArchiveSourceProvider) + ArchiveSourceProvider(
                mcReference
            )
        ),
        parent = ClassLoader.getSystemClassLoader()
    )

    // Resolves reference
    val minecraft = Archives.resolve(
        mcReference,
        mcLoader,
        Archives.Resolvers.ZIP_RESOLVER,
    )

    return minecraft.archive to manifest
}


internal fun loadMinecraftRef(
    mcVersion: String,
    path: Path,
    store: DataStore<String, LaunchMetadata>,
): DefaultMinecraftReference {
    val versionPath = path resolve mcVersion
    val minecraftPath = versionPath resolve "minecraft-${mcVersion}.jar"
    val mappingsPath = versionPath resolve "minecraft-mappings-${mcVersion}.txt"
    val assetsPath = versionPath resolve "assets"
    val assetsIndexesPath = assetsPath resolve "indexes"
    val assetsObjectsPath = assetsPath resolve "objects"

    val logger = Logger.getLogger("Minecraft Loader")

    val metadata = store[mcVersion] ?: run {
        logger.log(Level.INFO, "Downloading Minecraft version manifest for '$mcVersion'")

        val manifest = loadVersionManifest().find(mcVersion)
            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")
        val metadata = parseMetadata(manifest.metadata())

        store.put(mcVersion, metadata)

        metadata
    }

    if (minecraftPath.make()) {
        val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()
            ?: throw IllegalArgumentException("Cant find client in launch metadata?")

        logger.log(Level.INFO, "Downloading minecraft jar for '$mcVersion'")
        clientResource copyToBlocking minecraftPath
    }

    // Download mappings
    if (mappingsPath.make()) {
        val mappingsResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]?.toResource()
            ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")

        logger.log(Level.INFO, "Downloading minecraft client mappings for '$mcVersion'")
        mappingsResource copyToBlocking mappingsPath
    }

    val assetIndexCachePath = assetsIndexesPath resolve "${metadata.assetIndex.id}.json"
    if (assetIndexCachePath.make()) {
        logger.log(Level.INFO, "Couldnt find '$mcVersion' assets, downloading them.")

        val assetIndexCacheResource = metadata.assetIndex()
        assetIndexCacheResource copyToBlocking assetIndexCachePath

        parseAssetIndex(assetIndexCacheResource).objects.forEach { (name, asset) ->
            logger.log(Level.INFO, "Downloading asset: '$name'")

            val assetPath = assetsObjectsPath resolve asset.checksum.take(2) resolve asset.checksum
            if (assetPath.make()) {
                ExternalResource(
                    URI.create("$MINECRAFT_RESOURCES_URL/${asset.checksum.take(2)}/${asset.checksum}"),
                    HexFormat.of().parseHex(asset.checksum),
                    "SHA1"
                ) copyToBlocking assetPath
            }
        }
    }


    val libPath = versionPath resolve "lib"

    val libraries = DefaultMetadataProcessor().deriveDependencies(OsType.type, metadata)

    // Loads minecraft dependencies
    val minecraftDependencies = libraries
        .map {
            val desc = SimpleMavenDescriptor.parseDescription(it.name)!!
            val toPath = libPath resolve (desc.group.replace(
                '.',
                File.separatorChar
            )) resolve desc.artifact resolve desc.version resolve "${desc.artifact}-${desc.version}${if (desc.classifier == null) "" else "-${desc.classifier}"}.jar"
            if (toPath.make()) {
                it.downloads.artifact.toResource().copyToBlocking(toPath)
            }

            Archives.Finders.ZIP_FINDER.find(toPath)
        }

    // Loads minecraft reference
    val mcReference = Archives.find(minecraftPath, Archives.Finders.ZIP_FINDER)
    return DefaultMinecraftReference(
        mcVersion,
        mcReference,
        minecraftDependencies,
        metadata,
        ProGuardMappingParser.parse(mappingsPath.inputStream()),
        MinecraftReference.GameRuntimeInfo(
            assetsPath,
            metadata.assetIndex.id,
            versionPath
        )
    )
}