package dev.extframework.minecraft.bootstrapper

import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.loader.ResourceProvider
import java.nio.file.Path

public data class MinecraftNode(
    override val descriptor: MinecraftDescriptor,
    override val access: ArchiveAccessTree,
    val resources: ResourceProvider,
    val mappings: Path,
    val runtimeInfo: GameRuntimeInfo,
) : ArchiveNode<MinecraftDescriptor> {
    public data class GameRuntimeInfo(
        public val mainClass: String,
        public val assetsPath: Path,
        public val assetsName: String,
        public val gameDir: Path,
    )
}
