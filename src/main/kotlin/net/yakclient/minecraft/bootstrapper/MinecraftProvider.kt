package net.yakclient.minecraft.bootstrapper

import com.durganmcbroom.jobs.Job
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.loader.MutableClassLoader
import java.nio.file.Path

public interface MinecraftProvider<T: MinecraftReference> {
    public fun getReference(version: String, cachePath: Path) : Job<T>

    public fun get(
        ref: T,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ) : Job<MinecraftHandle>
}