package net.yakclient.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingSourceProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.launchermeta.handler.*
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.inputStream

public data class DefaultMinecraftReference(
    override val version: String,
    override val archive: ArchiveReference,
    val dependencies: List<ArchiveReference>,
    val manifest: LaunchMetadata, override val mappings: ArchiveMapping,
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
//
    val metadata = store[mcVersion] ?: run {
        val manifest = loadVersionManifest().find(mcVersion)
            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")
        val metadata = parseMetadata(manifest.metadata())

        store.put(mcVersion, metadata)

        metadata
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
        ProGuardMappingParser.parse(mappingsPath.inputStream())
    )
}