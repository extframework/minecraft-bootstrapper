package dev.extframework.minecraft.provider.def

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import dev.extframework.boot.archive.CacheHelper
import dev.extframework.common.util.Hex
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.*
import dev.extframework.minecraft.bootstrapper.MinecraftRepositorySettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.URL
import java.nio.file.Path
import java.util.*
import kotlin.math.floor

internal fun CacheHelper<*>.cacheLibs(
    libraries: List<MetadataLibrary>,
    processor: LaunchMetadataProcessor,
    resolver: MinecraftLibResolver,
) = asyncJob {
    libraries.first().extract

    libraries.mapAsync { lib ->
        cache(
            MinecraftLibArtifactRequest(
                SimpleMavenDescriptor.parseDescription(lib.name)!!,
                lib,
            ),
            MinecraftRepositorySettings,
            resolver
        )().merge()
    }.awaitAll()
}

internal suspend fun JobScope.downloadAssets(
    metadata: LaunchMetadata,
    assetsObjectsPath: Path,
    assetIndexCachePath: Path,
    dispatcher: CoroutineDispatcher,
) = withContext(dispatcher) {
    val assetIndexCacheResource = metadata.assetIndex().merge()
    val objects = parseAssetIndex(assetIndexCacheResource).merge().objects
    val totalSize = objects.values.sumOf { it.size }

    var bytesDownloaded = 0L
    val startTime = System.currentTimeMillis()

    val assetPaths = objects
        .entries
        .mapAsync { entry ->
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
                    Hex.parseHex(asset.checksum),
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

    assetIndexCacheResource copyTo assetIndexCachePath

    assetPaths
}

internal fun convertBytesToPrettyString(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")

    if (bytes == 0L) {
        return "0 B"
    }

    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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