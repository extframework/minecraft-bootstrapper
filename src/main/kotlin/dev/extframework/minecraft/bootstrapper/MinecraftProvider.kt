package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.jobs.Job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.loader.MutableClassLoader
import java.nio.file.Path

public interface MinecraftProvider {
    public fun get(
        version: String, cachePath: Path,
        archiveGraph: ArchiveGraph,
        classloader: MutableClassLoader
    ) : Job<MinecraftHandle>
}