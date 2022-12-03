package net.yakclient.minecraft.bootstrapper

import net.yakclient.archives.ArchiveReference

public interface MinecraftReference {
    public val version: String
    public val archive: ArchiveReference
}
