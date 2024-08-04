package dev.extframework.minecraft.bootstrapper

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.loader.MutableClassLoader
import java.nio.file.Path

public interface MinecraftProvider {
    public fun get(
        cachePath: Path,
        archiveGraph: ArchiveGraph,
    ) : MinecraftResolver<*>
}