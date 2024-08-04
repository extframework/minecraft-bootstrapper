package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import dev.extframework.boot.archive.ArchiveGraph
import java.nio.file.Path

public class MinecraftHandler(
    public val version: String,
    private val cache: Path,
    public val provider: MinecraftProvider,
    private val archiveGraph: ArchiveGraph,
) {
    public fun loadMinecraft(): AsyncJob<MinecraftNode> = asyncJob {
       val resolver = provider.get(
            cache,
            archiveGraph,
        )

        val descriptor = MinecraftDescriptor(version)
        archiveGraph.cacheAsync(
            MinecraftArtifactRequest(
                descriptor
            ),
            MinecraftRepositorySettings,
            resolver
        )().merge()

        archiveGraph.getAsync(
            descriptor,
            resolver
        )().merge()
    }
}