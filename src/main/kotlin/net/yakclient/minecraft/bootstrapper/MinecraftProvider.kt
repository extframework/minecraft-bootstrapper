package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.jobs.JobResult
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.loader.MutableClassLoader
import java.nio.file.Path

public interface MinecraftProvider<T: MinecraftReference> {
    public suspend fun getReference(version: String, cachePath: Path) : JobResult<T, Throwable>

    public fun get(
        ref: T,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ) : MinecraftHandle
}