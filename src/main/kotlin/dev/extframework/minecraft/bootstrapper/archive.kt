package dev.extframework.minecraft.bootstrapper

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import java.nio.file.Path

public data class MinecraftNode(
    override val descriptor: MinecraftDescriptor,
    override val access: ArchiveAccessTree,
    val archive: ArchiveReference,
    val runtimeInfo: GameRuntimeInfo,
) : ArchiveNode<MinecraftDescriptor> {
    public val libraries: List<MinecraftLibNode> = access
        .targets
        .map { it.relationship.node }
        .filterIsInstance<MinecraftLibNode>()

    public data class GameRuntimeInfo(
        public val mainClass: String,
        public val assetsPath: Path,
        public val assetsName: String,
        public val gameDir: Path,
        public val nativesPath: Path
    )
}

public data class MinecraftLibNode(
    override val descriptor: MinecraftLibDescriptor,
    val archive: ArchiveReference,
    override val access: ArchiveAccessTree,
) : ArchiveNode<MinecraftLibDescriptor>