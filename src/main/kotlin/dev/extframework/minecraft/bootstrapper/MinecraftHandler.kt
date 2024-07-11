package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.jobs.*
import dev.extframework.archives.ArchiveTree
import java.nio.file.Path
import dev.extframework.boot.archive.ArchiveGraph
import org.objectweb.asm.tree.ClassNode
import java.util.PriorityQueue

public class MinecraftHandler(
    public val version: String,
    private val cache: Path,
    public val provider: MinecraftProvider,
    private val archiveGraph: ArchiveGraph,
    private val defineClasses: Boolean
) {
    public fun loadMinecraft(parent: ClassLoader): Job<MinecraftHandle> = provider.get(
        version, cache,
        archiveGraph,
        MinecraftClassLoader(parent, defineClasses)
    )
}