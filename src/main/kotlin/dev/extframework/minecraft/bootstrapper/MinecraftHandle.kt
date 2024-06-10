package dev.extframework.minecraft.bootstrapper

import dev.extframework.archives.ArchiveHandle

public interface MinecraftHandle  {
    public val archive: ArchiveHandle
    public val libraries: List<ArchiveHandle>
    public fun start(args: Array<String>)

    public fun shutdown()
}