package dev.extframework.minecraft.bootstrapper

import dev.extframework.archives.ArchiveReference
import java.nio.file.Path

public interface MinecraftReference {
    public val version: String
    public val archive: ArchiveReference
    public val mappings: Path
    public val libraries: List<ArchiveReference>
    public val runtimeInfo: GameRuntimeInfo

    public data class GameRuntimeInfo(
        public val assetsPath: Path,
        public val assetsName: String,
        public val gameDir: Path,
    )
}
