package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.jobs.Job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.loader.MutableClassLoader
import java.nio.file.Path

public interface MinecraftProvider<T: MinecraftReference> {
    public fun getReference(version: String, cachePath: Path) : Job<T>

    public fun get(
        ref: T,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ) : Job<MinecraftHandle>
}