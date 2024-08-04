package dev.extframework.minecraft.provider.def

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.loader.MutableClassLoader
import dev.extframework.launchermeta.handler.MetadataLibrary
import dev.extframework.minecraft.bootstrapper.MinecraftProvider
import dev.extframework.minecraft.bootstrapper.MinecraftResolver
import java.nio.file.Path

public class DefaultMinecraftProvider : MinecraftProvider {
    override fun get(
        cachePath: Path,
        archiveGraph: ArchiveGraph,
    ): MinecraftResolver<*> {
        val libResolver = MinecraftLibResolver()
        archiveGraph.registerResolver(libResolver)
        MetadataLibrary::class.objectInstance
        return DefaultMinecraftResolver(
            cachePath,
            libResolver,
        )
    }
}
