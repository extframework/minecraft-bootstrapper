package net.yakclient.minecraft.bootstrapper.one_nineteen

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
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
import net.yakclient.minecraft.bootstrapper.MinecraftReference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path

private infix fun SafeResource.copyToBlocking(to: Path): Path = runBlocking { this@copyToBlocking copyTo to }

private const val LAUNCHER_META = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

//internal fun loadMinecraftRef(
//    mcVersion: String,
//    path: Path,
//    store: DataStore<String, ClientManifest>
//) : MinecraftReference
//
public data class Minecraft_1_19_Reference(
    override val archive: ArchiveReference,
    val dependencies: List<ArchiveReference>,
    val manifest: ClientManifest,
) : MinecraftReference

internal fun loadMinecraft(
    reference: Minecraft_1_19_Reference,
): Pair<ArchiveHandle, ClientManifest> {
    val (mcReference, minecraftDependencies, manifest) = reference

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
    store: DataStore<String, ClientManifest>,
): Minecraft_1_19_Reference {
    // Convert an operating system name to its type
    fun String.osNameToType(): OsType? = when (this) {
        "linux" -> OsType.UNIX
        "windows" -> OsType.WINDOWS
        "osx" -> OsType.OS_X
        else -> null
    }

    val versionPath = path resolve mcVersion
    val minecraftPath = versionPath resolve "minecraft-${mcVersion}.jar"
    val mappingsPath = versionPath resolve "minecraft-mappings-${mcVersion}.txt"

    // Get manifest or download manifest
    val manifest = store[mcVersion] ?: run {
        val url = URL(LAUNCHER_META)
        val conn = url.openConnection() as HttpURLConnection
        if (conn.responseCode != 200) throw IllegalStateException("Failed to load launcher metadata for minecraft! Was trying to load minecraft version: '$mcVersion' but it was not already cached.")

        data class LauncherManifestVersion(
            val id: String,
            val url: String,
            val sha1: String,
        )

        data class LauncherManifest(
            val versions: List<LauncherManifestVersion>,
        )


        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val launcherManifest = mapper.readValue<LauncherManifest>(conn.inputStream)

        val version = launcherManifest.versions.find { it.id == mcVersion }
            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: '$LAUNCHER_META'.")

        val manifest = mapper.readValue<ClientManifest>(URL(version.url).openStream())

        // Download minecraft jar
        if (minecraftPath.make()) {
            val client = (manifest.downloads[ManifestDownloadType.CLIENT]
                ?: throw IllegalStateException("Invalid client.json manifest. Must have a client download available!"))

            client.toResource().copyToBlocking(minecraftPath)
        }

        // Download mappings
        if (mappingsPath.make()) {
            val mappings = (manifest.downloads[ManifestDownloadType.CLIENT_MAPPINGS]
                ?: throw IllegalStateException("Invalid client.json manifest. Must have a client mappings download available!"))
            mappings.toResource().copyToBlocking(mappingsPath)
        }

        // Download manifest
        store.put(mcVersion, manifest)

        manifest
    }


    val libPath = versionPath resolve "lib"

    // Load libraries, from manifest
    val libraries: List<ClientLibrary> = manifest.libraries.filter { lib ->
        val allTypes = setOf(
            OsType.OS_X, OsType.WINDOWS, OsType.UNIX
        )

        val allowableOperatingSystems = if (lib.rules.isEmpty()) allTypes.toMutableSet()
        else lib.rules.filter { it.action == LibraryRuleAction.ALLOW }.flatMapTo(HashSet()) {
            it.osName?.osNameToType()?.let(::listOf) ?: allTypes
        }

        lib.rules.filter { it.action == LibraryRuleAction.DISALLOW }.forEach {
            it.osName?.osNameToType()?.let(allowableOperatingSystems::remove)
        }

        allowableOperatingSystems.contains(OsType.type)
    }

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
    return Minecraft_1_19_Reference(mcReference, minecraftDependencies, manifest)
}