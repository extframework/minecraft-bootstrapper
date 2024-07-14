package dev.extframework.minecraft.bootstrapper

import dev.extframework.archives.ArchiveHandle
import java.nio.file.Path

public interface MinecraftHandle  {
    public val archive: ArchiveHandle
    public val libraries: List<ArchiveHandle>
    public val info: GameRuntimeInfo

    public data class GameRuntimeInfo(
        public val mainClass: String,
        public val assetsPath: Path,
        public val assetsName: String,
        public val gameDir: Path,
    )
}