package net.yakclient.minecraft.bootstrapper

import net.yakclient.archives.ArchiveHandle

public interface MinecraftHandle  {
    public val archive: ArchiveHandle

    public fun start(args: Array<String>)

    public fun shutdown()


}